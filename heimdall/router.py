import os
import time
import json
import sqlite3
import hashlib
import secrets
import jwt
from pathlib import Path
from fastapi import APIRouter, Request, Response, HTTPException, Cookie, Form, Query
from fastapi.responses import HTMLResponse, FileResponse, RedirectResponse, JSONResponse

from pydantic import BaseModel
from constants import BASE_DIR, DB_PATH
from bifrost.auth import JWT_SECRET, JWT_ALGORITHM, hash_token
from bifrost.email_service import get_email_provider
from bifrost.audit import log_audit

router = APIRouter()
RESOLVED_STORAGE_ROOT = Path(BASE_DIR / "files").resolve()

def db():
    return sqlite3.connect(DB_PATH)

def safe_resolve(user_path: str | Path) -> Path:
    RESOLVED_STORAGE_ROOT.mkdir(parents=True, exist_ok=True)
    if isinstance(user_path, Path):
        user_path_str = str(user_path)
    else:
        user_path_str = user_path

    try:
        p = Path(user_path_str).resolve()
        p.relative_to(RESOLVED_STORAGE_ROOT)
        return p
    except ValueError:
        pass

    rel_path = user_path_str.lstrip("/")
    target = (RESOLVED_STORAGE_ROOT / rel_path).resolve()

    try:
        target.relative_to(RESOLVED_STORAGE_ROOT)
    except ValueError:
        raise HTTPException(400, "Directory traversal detected")

    return target

# --- Share Cookie Encryption Helpers ---
def encode_share_cookie(token: str, data: dict) -> str:
    payload = {
        "token": token,
        **data,
        "exp": time.time() + 86400  # 24 hours
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def decode_share_cookie(token: str, cookie_val: str) -> dict | None:
    try:
        payload = jwt.decode(cookie_val, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        if payload.get("token") == token:
            return payload
    except Exception:
        pass
    return None

# --- Share DB Helpers ---
def get_share_by_token(token: str) -> dict | None:
    token_hash = hash_token(token)
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT s.id, s.resource_type, s.resource_id, s.owner_user_id, s.permission, s.expires_at, s.revoked,
               sl.id, sl.requires_email, sl.allowed_emails, sl.password_hash, sl.max_uses, sl.use_count, sl.expires_at, sl.revoked
        FROM share_links sl
        JOIN shares s ON sl.share_id = s.id
        WHERE sl.token_hash = ?
        """,
        (token_hash,)
    )
    row = cur.fetchone()
    conn.close()
    if not row:
        return None
        
    return {
        "share_id": row[0],
        "resource_type": row[1],
        "resource_id": row[2],
        "owner_user_id": row[3],
        "permission": row[4],
        "share_expires_at": row[5],
        "share_revoked": bool(row[6]),
        "link_id": row[7],
        "requires_email": bool(row[8]),
        "allowed_emails": row[9],
        "password_hash": row[10],
        "max_uses": row[11],
        "use_count": row[12],
        "link_expires_at": row[13],
        "link_revoked": bool(row[14])
    }

def increment_share_use_count(link_id: int):
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("UPDATE share_links SET use_count = use_count + 1 WHERE id = ?", (link_id,))
        conn.commit()
    except Exception as e:
        print(f"Error incrementing use count: {e}")
    finally:
        conn.close()

def is_descendant(parent_path_str: str, child_path_str: str) -> bool:
    try:
        parent_p = Path(parent_path_str).resolve()
        child_p = Path(child_path_str).resolve()
        child_p.relative_to(parent_p)
        return True
    except ValueError:
        return False

def verify_file_belongs_to_share(share: dict, file_id: int) -> Path:
    """
    Checks if a file_id is accessible within the given share.
    Returns the resolved Path on success, raises HTTPException on failure.
    """
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path, is_file FROM files WHERE id = ? AND (trashed = 0 OR trashed IS NULL)", (file_id,))
    row = cur.fetchone()
    conn.close()
    if not row:
        raise HTTPException(404, "File not found")
    
    file_path_str, is_file = row
    resolved_path = safe_resolve(file_path_str)

    res_type = share["resource_type"]
    res_id = share["resource_id"]

    if res_type == "file":
        if file_id != res_id:
            raise HTTPException(403, "Access to file forbidden")
    elif res_type == "folder":
        # Fetch parent path
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT path FROM files WHERE id = ?", (res_id,))
        parent_row = cur.fetchone()
        conn.close()
        if not parent_row:
            raise HTTPException(404, "Shared folder parent not found")
        parent_path_str = parent_row[0]
        if not is_descendant(parent_path_str, file_path_str):
            raise HTTPException(403, "Access outside of shared folder forbidden")
    elif res_type == "album":
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM album_items WHERE album_id = ? AND media_id = ?", (res_id, file_id))
        count = cur.fetchone()[0]
        conn.close()
        if count == 0:
            raise HTTPException(403, "Access to album media forbidden")
    else:
        raise HTTPException(400, "Unknown resource type")

    return resolved_path

def check_share_access_state(share: dict, session_data: dict | None) -> str:
    """
    Evaluates gating rules. Returns:
    - 'authorized': All tests pass.
    - 'password_required': Needs password entry.
    - 'email_required': Needs email verification.
    - 'forbidden': Email is verified but not allowed in list.
    """
    # 1. Password Gating
    if share["password_hash"]:
        if not session_data or not session_data.get("password_verified"):
            return "password_required"
            
    # 2. Email Gating
    if share["requires_email"]:
        if not session_data or not session_data.get("email"):
            return "email_required"
        # Check allowed_emails
        email = session_data["email"].strip().lower()
        if share["allowed_emails"]:
            try:
                allowed = json.loads(share["allowed_emails"])
                if isinstance(allowed, list):
                    allowed_lower = [e.strip().lower() for e in allowed]
                    if email not in allowed_lower:
                        return "forbidden"
            except Exception:
                return "forbidden"
                
    return "authorized"

# --- HTML Template Wrapper ---
HTML_LAYOUT = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{title}</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: #f5f5f7;
            color: #1d1d1f;
            margin: 0;
            padding: 0;
        }}
        .container {{
            max-width: 1000px;
            margin: 0 auto;
            padding: 40px 20px;
        }}
        .card {{
            background: white;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.08);
            padding: 30px;
            max-width: 500px;
            margin: 100px auto;
            text-align: center;
        }}
        h1, h2 {{
            margin-top: 0;
        }}
        input[type="text"], input[type="password"], input[type="email"] {{
            width: 100%;
            padding: 12px;
            margin: 16px 0;
            box-sizing: border-box;
            border: 1px solid #ccc;
            border-radius: 8px;
            font-size: 16px;
        }}
        button {{
            background-color: #0071e3;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 16px;
            cursor: pointer;
            width: 100%;
        }}
        button:hover {{
            background-color: #0077ed;
        }}
        .error {{
            color: #d93025;
            margin-top: 15px;
            font-weight: 500;
        }}
        .success {{
            color: #137333;
            margin-top: 15px;
            font-weight: 500;
        }}
        /* Grid styles */
        .grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
            gap: 20px;
            margin-top: 20px;
        }}
        .grid-item {{
            background: white;
            border-radius: 10px;
            padding: 20px;
            text-align: center;
            box-shadow: 0 2px 6px rgba(0,0,0,0.04);
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
            text-decoration: none;
            color: inherit;
        }}
        .grid-item:hover {{
            transform: translateY(-2px);
            box-shadow: 0 6px 12px rgba(0,0,0,0.1);
        }}
        .icon {{
            font-size: 48px;
            margin-bottom: 12px;
        }}
        .name {{
            font-weight: 500;
            word-break: break-all;
            font-size: 14px;
            line-height: 1.4;
            max-height: 2.8em;
            overflow: hidden;
            text-overflow: ellipsis;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
        }}
        .meta {{
            font-size: 12px;
            color: #86868b;
            margin-top: 8px;
        }}
        .header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 25px;
            border-bottom: 1px solid #d2d2d7;
            padding-bottom: 15px;
        }}
        .breadcrumbs {{
            margin-bottom: 25px;
            font-size: 16px;
            font-weight: 500;
        }}
        .breadcrumbs a {{
            color: #0071e3;
            text-decoration: none;
        }}
        .breadcrumbs span {{
            color: #86868b;
        }}
        /* Preview styles */
        .preview-container {{
            background: white;
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.08);
            text-align: center;
        }}
        .preview-media {{
            max-width: 100%;
            max-height: 70vh;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }}
        iframe.preview-pdf {{
            width: 100%;
            height: 75vh;
            border: none;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }}
        .preview-header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            text-align: left;
        }}
        .preview-header h2 {{
            margin: 0;
            font-size: 20px;
        }}
        .back-link {{
            color: #0071e3;
            text-decoration: none;
            font-weight: 500;
        }}
    </style>
</head>
<body>
    <div class="container">
        {content}
    </div>
</body>
</html>
"""

# --- Public Shared Pages Router ---

@router.get("/s/{token}", response_class=HTMLResponse)
def get_shared_portal(token: str, request: Request, response: Response):
    ip = request.client.host if request.client else "unknown"
    share = get_share_by_token(token)
    if not share:
        log_audit(None, "share_access", token, ip, "failed")
        raise HTTPException(403, "Forbidden: Invalid share link")

    now = time.time()
    # Expired or revoked check
    if share["share_revoked"] or share["link_revoked"]:
        log_audit(share["owner_user_id"], "share_access", token, ip, "failed")
        raise HTTPException(403, "Forbidden: Share revoked")
    if (share["share_expires_at"] is not None and share["share_expires_at"] < now) or \
       (share["link_expires_at"] is not None and share["link_expires_at"] < now):
        log_audit(share["owner_user_id"], "share_access", token, ip, "failed")
        raise HTTPException(403, "Forbidden: Share expired")

    # Max uses check
    if share["max_uses"] is not None and share["use_count"] >= share["max_uses"]:
        log_audit(share["owner_user_id"], "share_access", token, ip, "failed")
        raise HTTPException(403, "Forbidden: Share overused")

    # Read session cookie
    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None

    # Check Gating
    state = check_share_access_state(share, session_data)
    
    if state == "password_required":
        log_audit(share["owner_user_id"], "share_password_prompt", token, ip, "success")
        content = f"""
        <div class="card">
            <h2>Password Required</h2>
            <p>This share link is password-protected.</p>
            <form method="POST" action="/s/{token}/verify-password">
                <input type="password" name="password" placeholder="Password" required autofocus>
                <button type="submit">Unlock</button>
            </form>
        </div>
        """
        return HTML_LAYOUT.format(title="Unlock Share", content=content)

    elif state == "email_required":
        log_audit(share["owner_user_id"], "share_email_prompt", token, ip, "success")
        content = f"""
        <div class="card">
            <h2>Email Verification Required</h2>
            <p>Please enter your email to receive an access link.</p>
            <form method="POST" action="/s/{token}/request-email-link">
                <input type="email" name="email" placeholder="Enter your email" required autofocus>
                <button type="submit">Verify Email</button>
            </form>
        </div>
        """
        return HTML_LAYOUT.format(title="Verify Email", content=content)

    elif state == "forbidden":
        log_audit(share["owner_user_id"], "share_email_forbidden", token, ip, "failed")
        raise HTTPException(403, "Forbidden: Your email is not allowed to access this share")

    # Access is authorized! Increment use_count if this is a new session load (no cookie yet)
    if not session_data:
        # Generate temporary cookie to mark session
        cookie_val = encode_share_cookie(token, {"guest": True})
        response.set_cookie(cookie_name, cookie_val, httponly=True)
        increment_share_use_count(share["link_id"])

    # Now render the main sharing interface
    res_type = share["resource_type"]
    res_id = share["resource_id"]

    log_audit(share["owner_user_id"], "share_access", token, ip, "success")

    if res_type == "file":
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT name, is_file FROM files WHERE id = ?", (res_id,))
        file_row = cur.fetchone()
        conn.close()
        if not file_row:
            raise HTTPException(404, "Shared file not found")
        
        # Redirect directly to preview file page
        return RedirectResponse(url=f"/s/{token}/file-preview/{res_id}")

    elif res_type == "folder":
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT name FROM files WHERE id = ?", (res_id,))
        folder_row = cur.fetchone()
        conn.close()
        folder_name = folder_row[0] if folder_row else "Shared Folder"

        content = f"""
        <div class="header">
            <h2 id="share-title">{folder_name}</h2>
            <a href="/s/{token}/download/{res_id}" id="download-all-btn" style="text-decoration: none; background: #0071e3; color: white; padding: 8px 16px; border-radius: 6px;">Download ZIP</a>
        </div>
        <div class="breadcrumbs" id="breadcrumbs"></div>
        <div class="grid" id="grid"></div>

        <script>
            const token = "{token}";
            const rootFolderId = {res_id};
            const resourceType = "{res_type}";
            let currentFolderId = rootFolderId;
            let navStack = [{{id: rootFolderId, name: "Home"}}];

            async function loadFolder(folderId) {{
                currentFolderId = folderId;
                const res = await fetch(`/s/${{token}}/list/${{folderId}}`);
                if (res.status === 403 || res.status === 401) {{
                    window.location.reload();
                    return;
                }}
                const data = await res.json();
                renderGrid(data.data);
                renderBreadcrumbs();
                
                // Update zip download link for folder
                const zipBtn = document.getElementById("download-all-btn");
                zipBtn.href = `/s/${{token}}/download/${{folderId}}`;
            }}

            function renderBreadcrumbs() {{
                const container = document.getElementById("breadcrumbs");
                container.innerHTML = "";
                navStack.forEach((item, index) => {{
                    if (index > 0) {{
                        const separator = document.createElement("span");
                        separator.innerText = " / ";
                        container.appendChild(separator);
                    }}
                    if (index === navStack.length - 1) {{
                        const span = document.createElement("span");
                        span.innerText = item.name;
                        container.appendChild(span);
                    }} else {{
                        const link = document.createElement("a");
                        link.href = "#";
                        link.innerText = item.name;
                        link.onclick = (e) => {{
                            e.preventDefault();
                            navStack = navStack.slice(0, index + 1);
                            loadFolder(item.id);
                        }};
                        container.appendChild(link);
                    }}
                }});
            }}

            function renderGrid(items) {{
                const grid = document.getElementById("grid");
                grid.innerHTML = "";
                if (items.length === 0) {{
                    grid.innerHTML = "<p style='grid-column: 1/-1; text-align: center; color: #86868b;'>This folder is empty</p>";
                    return;
                }}
                items.forEach(item => {{
                    const div = document.createElement("div");
                    div.className = "grid-item";
                    
                    let icon = "📄";
                    if (!item.is_file) {{
                        icon = "📁";
                    }} else {{
                        const ext = item.name.split('.').pop().toLowerCase();
                        if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(ext)) {{
                            icon = "🖼️";
                        }} else if (['mp4', 'webm', 'ogg'].includes(ext)) {{
                            icon = "🎥";
                        }} else if (ext === 'pdf') {{
                            icon = "📕";
                        }}
                    }}
                    
                    div.innerHTML = `
                        <div class="icon">${{icon}}</div>
                        <div class="name">${{item.name}}</div>
                        <div class="meta">${{item.is_file ? formatBytes(item.size) : 'Folder'}}</div>
                    `;
                    
                    div.onclick = () => {{
                        if (!item.is_file) {{
                            navStack.push({{id: item.id, name: item.name}});
                            loadFolder(item.id);
                        }} else {{
                            window.location.href = `/s/${{token}}/file-preview/${{item.id}}`;
                        }}
                    }};
                    grid.appendChild(div);
                }});
            }}

            function formatBytes(bytes, decimals = 2) {{
                if (bytes === 0) return '0 Bytes';
                const k = 1024;
                const dm = decimals < 0 ? 0 : decimals;
                const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
                const i = Math.floor(Math.log(bytes) / Math.log(k));
                return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
            }}

            loadFolder(rootFolderId);
        </script>
        """
        return HTML_LAYOUT.format(title=folder_name, content=content)

    elif res_type == "album":
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT name FROM albums WHERE id = ?", (res_id,))
        album_row = cur.fetchone()
        conn.close()
        album_name = album_row[0] if album_row else "Shared Album"

        content = f"""
        <div class="header">
            <h2 id="share-title">{album_name}</h2>
        </div>
        <div class="grid" id="grid"></div>

        <script>
            const token = "{token}";
            const rootAlbumId = {res_id};

            async function loadAlbum() {{
                const res = await fetch(`/s/${{token}}/album-contents`);
                if (res.status === 403 || res.status === 401) {{
                    window.location.reload();
                    return;
                }}
                const data = await res.json();
                renderGrid(data.data);
            }}

            function renderGrid(items) {{
                const grid = document.getElementById("grid");
                grid.innerHTML = "";
                if (items.length === 0) {{
                    grid.innerHTML = "<p style='grid-column: 1/-1; text-align: center; color: #86868b;'>This album is empty</p>";
                    return;
                }}
                items.forEach(item => {{
                    const div = document.createElement("div");
                    div.className = "grid-item";
                    
                    let icon = "📄";
                    const ext = item.name.split('.').pop().toLowerCase();
                    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(ext)) {{
                        icon = "🖼️";
                    }} else if (['mp4', 'webm', 'ogg'].includes(ext)) {{
                        icon = "🎥";
                    }}
                    
                    div.innerHTML = `
                        <div class="icon">${{icon}}</div>
                        <div class="name">${{item.name}}</div>
                        <div class="meta">Media File</div>
                    `;
                    
                    div.onclick = () => {{
                        window.location.href = `/s/${{token}}/file-preview/${{item.id}}`;
                    }};
                    grid.appendChild(div);
                }});
            }}

            loadAlbum();
        </script>
        """
        return HTML_LAYOUT.format(title=album_name, content=content)

    return HTMLResponse("Unsupported Resource Type", status_code=400)


@router.post("/s/{token}/verify-password")
def post_verify_password(token: str, request: Request, password: str = Form(...)):
    ip = request.client.host if request.client else "unknown"
    share = get_share_by_token(token)
    if not share or not share["password_hash"]:
        log_audit(None, "share_password_verify", token, ip, "failed")
        raise HTTPException(403, "Invalid request")

    # Hash and verify password
    hashed_pwd = hashlib.sha256(password.encode()).hexdigest()
    if hashed_pwd != share["password_hash"]:
        log_audit(share["owner_user_id"], "share_password_verify", token, ip, "failed")
        # Render page again with error
        content = f"""
        <div class="card">
            <h2>Password Required</h2>
            <p>This share link is password-protected.</p>
            <form method="POST" action="/s/{token}/verify-password">
                <input type="password" name="password" placeholder="Password" required autofocus>
                <button type="submit">Unlock</button>
            </form>
            <p class="error">Incorrect password. Please try again.</p>
        </div>
        """
        return HTMLResponse(HTML_LAYOUT.format(title="Unlock Share", content=content), status_code=403)

    # Success: Set cookie
    log_audit(share["owner_user_id"], "share_password_verify", token, ip, "success")
    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    existing_data = decode_share_cookie(token, cookie_val) if cookie_val else {}
    existing_data["password_verified"] = True

    response = RedirectResponse(url=f"/s/{token}", status_code=303)
    # Re-encode session cookie
    new_val = encode_share_cookie(token, existing_data)
    response.set_cookie(cookie_name, new_val, httponly=True)
    
    # Check if we should increment use count here (if no previous valid session existed)
    if not cookie_val:
        increment_share_use_count(share["link_id"])
        
    return response


@router.post("/s/{token}/request-email-link")
def post_request_email_link(token: str, request: Request, email: str = Form(...)):
    ip = request.client.host if request.client else "unknown"
    share = get_share_by_token(token)
    if not share or not share["requires_email"]:
        log_audit(None, "share_email_request", token, ip, "failed")
        raise HTTPException(403, "Invalid request")

    email = email.strip().lower()
    
    # Check allowed_emails
    allowed = True
    if share["allowed_emails"]:
        try:
            allowed_list = json.loads(share["allowed_emails"])
            if isinstance(allowed_list, list):
                allowed_lower = [e.strip().lower() for e in allowed_list]
                if email not in allowed_lower:
                    allowed = False
        except Exception:
            allowed = False

    if not allowed:
        log_audit(share["owner_user_id"], "share_email_request", f"{token}:{email}", ip, "failed")
        content = f"""
        <div class="card">
            <h2>Access Denied</h2>
            <p>The email <strong>{email}</strong> is not authorized to access this share link.</p>
            <a href="/s/{token}" class="back-link">Try another email</a>
        </div>
        """
        return HTMLResponse(HTML_LAYOUT.format(title="Access Denied", content=content), status_code=403)

    # Generate custom email-gate verification magic link
    magic_payload = {
        "email": email,
        "token": token,
        "exp": time.time() + 900  # 15 minutes
    }
    magic_token = jwt.encode(magic_payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    
    base_url = str(request.base_url).rstrip("/")
    verify_url = f"{base_url}/s/{token}/verify-email?token={magic_token}"

    provider = get_email_provider()
    success = provider.send_magic_link(email, verify_url)

    if not success:
        log_audit(share["owner_user_id"], "share_email_request", f"{token}:{email}", ip, "failed")
        raise HTTPException(500, "Failed to dispatch verification email")

    log_audit(share["owner_user_id"], "share_email_request", f"{token}:{email}", ip, "success")
    content = f"""
    <div class="card">
        <h2>Verification Link Sent</h2>
        <p>A magic link has been sent to <strong>{email}</strong>. Please check your inbox and click the link to proceed.</p>
        <p class="success">Check console/mock mail provider logs if testing locally.</p>
    </div>
    """
    return HTMLResponse(HTML_LAYOUT.format(title="Email Sent", content=content))


@router.get("/s/{token}/verify-email")
def get_verify_email(token: str, request: Request):
    ip = request.client.host if request.client else "unknown"
    magic_token = request.query_params.get("token")
    if not magic_token:
        log_audit(None, "share_email_verify", token, ip, "failed")
        raise HTTPException(400, "Missing verification token")

    try:
        payload = jwt.decode(magic_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        if payload.get("token") != token:
            raise HTTPException(400, "Token mismatch")
        email = payload.get("email")
    except Exception:
        log_audit(None, "share_email_verify", token, ip, "failed")
        raise HTTPException(401, "Invalid or expired magic link token")

    # Get share link and recheck
    share = get_share_by_token(token)
    if not share or not share["requires_email"]:
        raise HTTPException(403, "Invalid share link")

    log_audit(share["owner_user_id"], "share_email_verify", f"{token}:{email}", ip, "success")

    # Update session cookie
    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    existing_data = decode_share_cookie(token, cookie_val) if cookie_val else {}
    existing_data["email"] = email

    response = RedirectResponse(url=f"/s/{token}")
    new_val = encode_share_cookie(token, existing_data)
    response.set_cookie(cookie_name, new_val, httponly=True)
    
    # Increment use count if no prior session existed
    if not cookie_val:
        increment_share_use_count(share["link_id"])
        
    return response


@router.get("/s/{token}/file-preview/{file_id}", response_class=HTMLResponse)
def get_file_preview_page(token: str, file_id: int, request: Request):
    ip = request.client.host if request.client else "unknown"
    share = get_share_by_token(token)
    if not share:
        raise HTTPException(403, "Forbidden")

    # Verify session cookie
    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None

    # Check Gating
    state = check_share_access_state(share, session_data)
    if state != "authorized":
        raise HTTPException(403, "Unauthorized share access")

    # Verify file is inside share
    resolved_path = verify_file_belongs_to_share(share, file_id)

    ext = resolved_path.suffix.lower()
    filename = resolved_path.name

    # Determine preview element
    preview_element = ""
    if ext in ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp']:
        preview_element = f'<img class="preview-media" src="/s/{token}/view/{file_id}">'
    elif ext == '.pdf':
        preview_element = f'<iframe class="preview-pdf" src="/s/{token}/view/{file_id}"></iframe>'
    elif ext in ['.mp4', '.webm', '.ogg']:
        preview_element = f'<video class="preview-media" controls><source src="/s/{token}/view/{file_id}"></video>'
    else:
        preview_element = '<p style="font-size: 16px; color: #86868b; margin: 40px 0;">Preview not available for this file type.</p>'

    content = f"""
    <div class="preview-container">
        <div class="preview-header">
            <h2>{filename}</h2>
            <a href="/s/{token}" class="back-link">Back to Share</a>
        </div>
        <div style="margin: 20px 0;">
            {preview_element}
        </div>
        <div style="margin-top: 25px;">
            <a href="/s/{token}/download/{file_id}" style="text-decoration: none; display: inline-block; background: #0071e3; color: white; padding: 12px 24px; border-radius: 8px; font-weight: 500;">Download File</a>
        </div>
    </div>
    """
    return HTML_LAYOUT.format(title=filename, content=content)


@router.get("/s/{token}/list/{folder_id}")
def get_shared_folder_list(token: str, folder_id: int, request: Request):
    share = get_share_by_token(token)
    if not share or share["resource_type"] != "folder":
        raise HTTPException(403, "Forbidden")

    # Verify session
    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None
    if check_share_access_state(share, session_data) != "authorized":
        raise HTTPException(403, "Unauthorized")

    # Get parent folder path
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM files WHERE id = ?", (share["resource_id"],))
    parent_row = cur.fetchone()
    if not parent_row:
        conn.close()
        raise HTTPException(404, "Shared folder parent not found")
    parent_path_str = parent_row[0]

    # Get current folder path
    cur.execute("SELECT path FROM files WHERE id = ? AND is_file = 0 AND (trashed = 0 OR trashed IS NULL)", (folder_id,))
    folder_row = cur.fetchone()
    if not folder_row:
        conn.close()
        raise HTTPException(404, "Folder not found")
    folder_path_str = folder_row[0]

    # Descendant check
    if not is_descendant(parent_path_str, folder_path_str):
        conn.close()
        raise HTTPException(403, "Access outside of shared folder forbidden")

    # Fetch children
    cur.execute(
        "SELECT id, name, is_file, size, modified FROM files WHERE parent = ? AND (trashed = 0 OR trashed IS NULL) ORDER BY name ASC",
        (folder_path_str,)
    )
    rows = cur.fetchall()
    conn.close()

    data = [
        {
            "id": r[0],
            "name": r[1],
            "is_file": bool(r[2]),
            "size": r[3],
            "modified": r[4]
        }
        for r in rows
    ]
    return {"data": data}


@router.get("/s/{token}/album-contents")
def get_shared_album_contents(token: str, request: Request):
    share = get_share_by_token(token)
    if not share or share["resource_type"] != "album":
        raise HTTPException(403, "Forbidden")

    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None
    if check_share_access_state(share, session_data) != "authorized":
        raise HTTPException(403, "Unauthorized")

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT f.id, f.name, f.size, f.modified
        FROM album_items ai
        JOIN files f ON ai.media_id = f.id
        WHERE ai.album_id = ? AND (f.trashed = 0 OR f.trashed IS NULL)
        ORDER BY f.name ASC
        """,
        (share["resource_id"],)
    )
    rows = cur.fetchall()
    conn.close()

    data = [
        {
            "id": r[0],
            "name": r[1],
            "is_file": True,
            "size": r[2],
            "modified": r[3]
        }
        for r in rows
    ]
    return {"data": data}


@router.get("/s/{token}/download/{file_id}")
def download_shared_file(token: str, file_id: int, request: Request):
    share = get_share_by_token(token)
    if not share:
        raise HTTPException(403, "Forbidden")

    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None
    if check_share_access_state(share, session_data) != "authorized":
        raise HTTPException(403, "Unauthorized")

    # If it is a folder, zip it and download
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path, is_file, name FROM files WHERE id = ? AND (trashed = 0 OR trashed IS NULL)", (file_id,))
    row = cur.fetchone()
    conn.close()
    if not row:
        raise HTTPException(404, "Item not found")

    item_path_str, is_file, name = row
    resolved_path = verify_file_belongs_to_share(share, file_id)

    if not resolved_path.exists():
        raise HTTPException(404, "File does not exist on disk")

    if is_file:
        # Enforce content disposition for non-preview types (e.g. SVG/HTML/txt etc)
        ext = resolved_path.suffix.lower()
        # Preview allowed types
        preview_exts = {'.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.pdf', '.mp4', '.webm', '.ogg'}
        if ext in preview_exts:
            # Inline viewing response
            return FileResponse(resolved_path)
        else:
            # Force download response
            return FileResponse(resolved_path, filename=name, content_disposition_type="attachment")
    else:
        # Zip the directory
        import tempfile
        import shutil
        temp_dir = Path(tempfile.gettempdir())
        temp_zip_base = temp_dir / f"share_folder_{file_id}_{int(time.time())}"
        try:
            zip_file_path_str = shutil.make_archive(
                base_name=str(temp_zip_base),
                format="zip",
                root_dir=str(resolved_path)
            )
            return FileResponse(
                zip_file_path_str,
                filename=f"{name}.zip",
                content_disposition_type="attachment"
            )
        except Exception as e:
            raise HTTPException(500, f"Failed to zip folder: {e}")


@router.get("/s/{token}/view/{file_id}")
def view_shared_file_inline(token: str, file_id: int, request: Request):
    share = get_share_by_token(token)
    if not share:
        raise HTTPException(403, "Forbidden")

    cookie_name = f"share_session_{token}"
    cookie_val = request.cookies.get(cookie_name)
    session_data = decode_share_cookie(token, cookie_val) if cookie_val else None
    if check_share_access_state(share, session_data) != "authorized":
        raise HTTPException(403, "Unauthorized")

    resolved_path = verify_file_belongs_to_share(share, file_id)

    if not resolved_path.exists() or resolved_path.is_dir():
        raise HTTPException(404, "File not found")

    ext = resolved_path.suffix.lower()
    preview_exts = {'.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.pdf', '.mp4', '.webm', '.ogg'}
    if ext in preview_exts:
        # Return FileResponse which renders inline in browser
        return FileResponse(resolved_path)
    else:
        # Block inline rendering for safety, force download
        return FileResponse(resolved_path, filename=resolved_path.name, content_disposition_type="attachment")


# --- Owner Management APIs (Protected by Bifrost gateway auth middleware) ---

class CreateSharePayload(BaseModel):
    resource_type: str  # 'file' | 'folder' | 'album'
    resource_id: int
    permission: str = "view"  # 'view' | 'comment' | 'edit'
    expires_in: float | None = None  # relative seconds from now, optional
    requires_email: bool = False
    allowed_emails: list[str] | None = None  # JSON array of emails, optional
    password: str | None = None
    max_uses: int | None = None

@router.post("/mimir/shares")
def post_create_share(payload: CreateSharePayload, request: Request):
    user_id = request.state.user_id
    if not user_id:
        raise HTTPException(401, "Unauthenticated")

    # Verify resource exists and belongs to the user or user is owner of lore
    is_owner = request.state.is_owner
    conn = db()
    cur = conn.cursor()

    if payload.resource_type in ["file", "folder"]:
        cur.execute("SELECT owner_user_id FROM files WHERE id = ?", (payload.resource_id,))
        row = cur.fetchone()
        if not row:
            conn.close()
            raise HTTPException(404, f"Resource {payload.resource_type} not found")
        if not is_owner and row[0] != user_id:
            conn.close()
            raise HTTPException(403, "Access to resource forbidden")
    elif payload.resource_type == "album":
        cur.execute("SELECT owner_user_id FROM albums WHERE id = ?", (payload.resource_id,))
        row = cur.fetchone()
        if not row:
            conn.close()
            raise HTTPException(404, "Album not found")
        if not is_owner and row[0] != user_id:
            conn.close()
            raise HTTPException(403, "Access to album forbidden")
    else:
        conn.close()
        raise HTTPException(400, "Invalid resource type")

    # Generate token
    token = secrets.token_hex(16)
    token_hash = hash_token(token)

    now = time.time()
    expires_at = now + payload.expires_in if payload.expires_in is not None else None

    # Hashed password if present
    password_hash = None
    if payload.password:
        password_hash = hashlib.sha256(payload.password.encode()).hexdigest()

    allowed_emails_str = json.dumps(payload.allowed_emails) if payload.allowed_emails is not None else None

    try:
        # Insert share
        cur.execute(
            """
            INSERT INTO shares (resource_type, resource_id, owner_user_id, shared_with_user_id, permission, created, expires_at, revoked)
            VALUES (?, ?, ?, NULL, ?, ?, ?, 0)
            """,
            (payload.resource_type, payload.resource_id, user_id, payload.permission, now, expires_at)
        )
        share_id = cur.lastrowid

        # Insert share link
        cur.execute(
            """
            INSERT INTO share_links (share_id, token_hash, requires_email, allowed_emails, password_hash, max_uses, use_count, created, expires_at, revoked)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, 0)
            """,
            (share_id, token_hash, 1 if payload.requires_email else 0, allowed_emails_str, password_hash, payload.max_uses, now, expires_at)
        )
        link_id = cur.lastrowid
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(500, f"Database transaction failed: {e}")
    finally:
        conn.close()

    log_audit(user_id, "share_created", f"share:{share_id}", request.client.host if request.client else "unknown", "success")

    return {
        "share_id": share_id,
        "share_link_id": link_id,
        "token": token,
        "share_url": f"/s/{token}"
    }


@router.get("/mimir/shares")
def get_shares_list(request: Request):
    user_id = request.state.user_id
    if not user_id:
        raise HTTPException(401, "Unauthenticated")

    is_owner = request.state.is_owner
    conn = db()
    cur = conn.cursor()

    if is_owner:
        cur.execute(
            """
            SELECT s.id, s.resource_type, s.resource_id, s.permission, s.expires_at, s.revoked,
                   sl.id, sl.requires_email, sl.allowed_emails, sl.max_uses, sl.use_count, sl.token_hash
            FROM shares s
            JOIN share_links sl ON sl.share_id = s.id
            """
        )
    else:
        cur.execute(
            """
            SELECT s.id, s.resource_type, s.resource_id, s.permission, s.expires_at, s.revoked,
                   sl.id, sl.requires_email, sl.allowed_emails, sl.max_uses, sl.use_count, sl.token_hash
            FROM shares s
            JOIN share_links sl ON sl.share_id = s.id
            WHERE s.owner_user_id = ?
            """,
            (user_id,)
        )

    rows = cur.fetchall()
    conn.close()

    shares_list = []
    for r in rows:
        shares_list.append({
            "share_id": r[0],
            "resource_type": r[1],
            "resource_id": r[2],
            "permission": r[3],
            "expires_at": r[4],
            "revoked": bool(r[5]),
            "link_id": r[6],
            "requires_email": bool(r[7]),
            "allowed_emails": json.loads(r[8]) if r[8] else None,
            "max_uses": r[9],
            "use_count": r[10]
        })
    return {"shares": shares_list}


@router.delete("/mimir/shares/{share_id}")
def delete_share(share_id: int, request: Request):
    user_id = request.state.user_id
    if not user_id:
        raise HTTPException(401, "Unauthenticated")

    is_owner = request.state.is_owner
    conn = db()
    cur = conn.cursor()

    # Get owner of share
    cur.execute("SELECT owner_user_id FROM shares WHERE id = ?", (share_id,))
    row = cur.fetchone()
    if not row:
        conn.close()
        raise HTTPException(404, "Share not found")

    if not is_owner and row[0] != user_id:
        conn.close()
        raise HTTPException(403, "Access to share forbidden")

    try:
        # Mark as revoked or delete from database. Let's delete it so it is permanently revoked.
        cur.execute("DELETE FROM share_links WHERE share_id = ?", (share_id,))
        cur.execute("DELETE FROM shares WHERE id = ?", (share_id,))
        conn.commit()
    except Exception as e:
        conn.rollback()
        raise HTTPException(500, f"Delete transaction failed: {e}")
    finally:
        conn.close()

    log_audit(user_id, "share_revoked", f"share:{share_id}", request.client.host if request.client else "unknown", "success")

    return {"status": "ok", "message": "Share revoked successfully"}
