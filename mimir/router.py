import os
import time
import sqlite3
import shutil
import hashlib
import uuid
import threading
from pathlib import Path
from fastapi import APIRouter, File, HTTPException, Query, UploadFile, Request, BackgroundTasks
from fastapi.responses import FileResponse
from pydantic import BaseModel

from constants import BASE_DIR, DB_PATH

router = APIRouter()
RESOLVED_STORAGE_ROOT = Path(BASE_DIR / "files").resolve()
RESOLVED_STORAGE_ROOT.mkdir(parents=True, exist_ok=True)

TEMP_UPLOAD_DIR = RESOLVED_STORAGE_ROOT / ".tmp_uploads"
TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# Keep track of chunked upload sessions
upload_sessions = {}

def db():
    return sqlite3.connect(DB_PATH)

def delete_chromadb_embeddings(file_ids):
    if not file_ids:
        return
    try:
        from psyche.init import get_collection
        collection = get_collection()
        collection.delete(ids=[str(fid) for fid in file_ids])
    except Exception as e:
        print(f"Error deleting from ChromaDB: {e}")

def safe_resolve(user_path: str | Path) -> Path:
    RESOLVED_STORAGE_ROOT.mkdir(parents=True, exist_ok=True)
    if isinstance(user_path, Path):
        user_path_str = str(user_path)
    else:
        user_path_str = user_path

    # Check if user_path_str is already resolved under RESOLVED_STORAGE_ROOT
    try:
        p = Path(user_path_str).resolve()
        p.relative_to(RESOLVED_STORAGE_ROOT)
        return p
    except ValueError:
        pass

    # Treat as relative to RESOLVED_STORAGE_ROOT
    rel_path = user_path_str.lstrip("/")
    target = (RESOLVED_STORAGE_ROOT / rel_path).resolve()

    try:
        target.relative_to(RESOLVED_STORAGE_ROOT)
    except ValueError:
        raise HTTPException(400, "Directory traversal detected")

    return target

def calculate_sha256(file_path: Path) -> str:
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(8192), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def purge_old_trash():
    # Items trashed > 30 days. 30 days in seconds = 30 * 24 * 60 * 60 = 2592000
    cutoff = time.time() - 2592000
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT id, path, is_file FROM files WHERE trashed = 1 AND trashed_at < ?", (cutoff,))
        rows = cur.fetchall()
        file_ids = []
        for fid, path_str, is_file in rows:
            p = Path(path_str)
            if p.exists():
                if is_file:
                    try:
                        p.unlink()
                    except Exception:
                        pass
                else:
                    try:
                        shutil.rmtree(p)
                    except Exception:
                        pass
            file_ids.append(fid)
        cur.execute("DELETE FROM files WHERE trashed = 1 AND trashed_at < ?", (cutoff,))
        if file_ids:
            delete_chromadb_embeddings(file_ids)
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"Error during trash auto-purge: {e}")
    finally:
        conn.close()

def start_trash_purger():
    def run_purger():
        # Sleep briefly then run, then run every 24 hours
        time.sleep(5)
        while True:
            try:
                purge_old_trash()
            except Exception as e:
                print(f"Error in trash purge thread: {e}")
            time.sleep(86400)

    thread = threading.Thread(target=run_purger, daemon=True)
    thread.start()

# Start the background purger thread
start_trash_purger()


# Download a file or zipped folder by database file ID
@router.get("/download/{file_id:int}")
def download_by_id(file_id: int, background_tasks: BackgroundTasks):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path, is_file, name FROM files WHERE id = ? AND (trashed = 0 OR trashed IS NULL)", (file_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "File not found")

    path_str, is_file, name = row
    target = safe_resolve(path_str)

    if not target.exists():
        raise HTTPException(404, "File does not exist on disk")

    if is_file:
        return FileResponse(target, filename=name)
    else:
        # Zip the directory
        import tempfile
        temp_zip_base = TEMP_UPLOAD_DIR / f"folder_{file_id}_{int(time.time())}"
        try:
            zip_file_path_str = shutil.make_archive(
                base_name=str(temp_zip_base),
                format="zip",
                root_dir=str(target)
            )
            zip_file_path = Path(zip_file_path_str)

            def cleanup_temp_file(path_to_clean: Path):
                if path_to_clean.exists():
                    path_to_clean.unlink()

            background_tasks.add_task(cleanup_temp_file, zip_file_path)

            return FileResponse(
                zip_file_path,
                filename=f"{name}.zip",
                background=background_tasks
            )
        except Exception as e:
            raise HTTPException(500, f"Failed to create zip archive: {str(e)}")


# Download a file (original path-based route)
@router.get("/download/{path:path}")
def fetch_file(path: str):
    full_path = safe_resolve(path)

    if not full_path.exists() or full_path.is_dir():
        raise HTTPException(404, "File not found")

    # Verify not trashed
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT trashed FROM files WHERE path = ?", (str(full_path),))
    row = cur.fetchone()
    conn.close()
    if row and row[0]:
        raise HTTPException(404, "File not found")

    return FileResponse(full_path, filename=full_path.name)


# Upload a file to the respective path
@router.post("/upload")
async def upload_file(
    path: str = Query("/", description="Folder to upload to"),
    file: UploadFile = File(...)
):
    folder = safe_resolve(path)
    folder.mkdir(parents=True, exist_ok=True)

    save_path = safe_resolve(folder / file.filename)

    if save_path.exists():
        raise HTTPException(400, "File already exists")

    content = await file.read()
    save_path.write_bytes(content)

    # Compute SHA-256
    sha = calculate_sha256(save_path)

    # Deduplication
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT path FROM files WHERE sha256 = ? AND is_file = 1 AND (trashed = 0 OR trashed IS NULL) LIMIT 1", (sha,))
        row = cur.fetchone()
        if row:
            existing_path = Path(row[0])
            if existing_path.exists() and existing_path != save_path:
                save_path.unlink()
                os.link(existing_path, save_path)

        cur.execute(
            """
            INSERT OR IGNORE INTO files (path, name, parent, is_file, size, modified, created, sha256, trashed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            (
                str(save_path),
                file.filename,
                str(folder),
                1,
                len(content),
                time.time(),
                time.time(),
                sha,
            ),
        )
        conn.commit()
    except Exception as e:
        conn.rollback()
        if save_path.exists():
            save_path.unlink()
        raise HTTPException(500, f"Upload transaction failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "saved_as": save_path.name, "sha256": sha}


# List files and Sub Dirs
@router.get("/list")
def get_list(
    path: str = Query("/", description="Folder to list"),
    page: int = 1,
    limit: int = 20,
):
    folder = safe_resolve(path)

    if not folder.exists() or not folder.is_dir():
        raise HTTPException(404, "Folder not found")

    conn = db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, name, is_file, size, modified, favorite FROM files WHERE parent = ? AND (trashed = 0 OR trashed IS NULL) ORDER BY name ASC",
        (str(folder),),
    )
    rows = cur.fetchall()
    conn.close()

    start = (page - 1) * limit
    sliced = rows[start:start + limit]

    data = [
        {
            "id": r[0],
            "name": r[1],
            "is_file": bool(r[2]),
            "size": r[3],
            "modified": r[4],
            "favorite": bool(r[5]),
        }
        for r in sliced
    ]

    return {
        "page": page,
        "limit": limit,
        "total": len(rows),
        "data": data,
    }


# Create a folder under the respective path
@router.post("/create_folder")
def create_folder(path: str = "/", name: str = ""):
    if not name:
        raise HTTPException(400, "Folder name required")

    parent = safe_resolve(path)
    new_folder = safe_resolve(parent / name)

    if new_folder.exists():
        raise HTTPException(400, "Folder already exists")

    new_folder.mkdir(parents=True, exist_ok=False)

    conn = db()
    cur = conn.cursor()
    try:
        cur.execute(
            """
            INSERT OR IGNORE INTO files (path, name, parent, is_file, size, modified, created, trashed)
            VALUES (?, ?, ?, 0, 0, ?, ?, 0)
            """,
            (str(new_folder), name, str(parent), time.time(), time.time()),
        )
        conn.commit()
    except Exception as e:
        conn.rollback()
        try:
            new_folder.rmdir()
        except Exception:
            pass
        raise HTTPException(500, f"Database transaction failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "created": str(new_folder)}


# Delete a file or folder (trashed by default)
@router.delete("/delete")
def delete(request: Request, path: str = "/", permanent: bool = Query(False)):
    from bifrost.audit import log_audit
    user_id = request.state.user_id if hasattr(request.state, "user_id") else None
    ip = request.client.host if request.client else "unknown"

    target = safe_resolve(path)

    if not target.exists():
        log_audit(user_id, "delete_item", path, ip, "failed")
        raise HTTPException(404, "Path not found")

    conn = db()
    cur = conn.cursor()
    try:
        if permanent:
            # Permanent delete
            file_ids = []
            if target.is_file():
                target.unlink()
                cur.execute("SELECT id FROM files WHERE path = ?", (str(target),))
                row = cur.fetchone()
                if row:
                    file_ids.append(row[0])
                cur.execute("DELETE FROM files WHERE path = ?", (str(target),))
            else:
                for item in list(target.glob("**/*")):
                    resolved_item = safe_resolve(item)
                    if resolved_item.is_file():
                        resolved_item.unlink()
                        cur.execute("SELECT id FROM files WHERE path = ?", (str(resolved_item),))
                        row = cur.fetchone()
                        if row:
                            file_ids.append(row[0])
                        cur.execute("DELETE FROM files WHERE path = ?", (str(resolved_item),))
                for item in sorted(list(target.glob("**/*")), key=lambda p: len(str(p)), reverse=True):
                    resolved_item = safe_resolve(item)
                    if resolved_item.is_dir():
                        resolved_item.rmdir()
                        cur.execute("SELECT id FROM files WHERE path = ?", (str(resolved_item),))
                        row = cur.fetchone()
                        if row:
                            file_ids.append(row[0])
                        cur.execute("DELETE FROM files WHERE path = ?", (str(resolved_item),))
                target.rmdir()
                cur.execute("SELECT id FROM files WHERE path = ?", (str(target),))
                row = cur.fetchone()
                if row:
                    file_ids.append(row[0])
                cur.execute("DELETE FROM files WHERE path = ?", (str(target),))
            if file_ids:
                delete_chromadb_embeddings(file_ids)
        else:
            # Move to trash
            if target == RESOLVED_STORAGE_ROOT:
                raise HTTPException(400, "Cannot trash storage root")
            trashed_time = time.time()
            cur.execute(
                "UPDATE files SET trashed = 1, trashed_at = ? WHERE path = ? OR path LIKE ?",
                (trashed_time, str(target), str(target) + "/%")
            )
        conn.commit()
        log_audit(user_id, "delete_item", path, ip, "success")
    except Exception as e:
        conn.rollback()
        log_audit(user_id, "delete_item", path, ip, "failed")
        if isinstance(e, HTTPException):
            raise e
        raise HTTPException(500, f"Delete failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "deleted": path, "permanent": permanent}


# Rename a file or folder (name-only)
@router.put("/rename")
def rename(path: str = "/", name: str = ""):
    if not name:
        raise HTTPException(400, "New name required")

    target = safe_resolve(path)

    if not target.exists():
        raise HTTPException(404, "Path not found")

    if target == RESOLVED_STORAGE_ROOT:
        raise HTTPException(400, "Cannot rename storage root")

    new_path = safe_resolve(target.parent / name)

    if new_path.exists():
        raise HTTPException(400, "Name already exists")

    # Rename on filesystem
    try:
        target.rename(new_path)
    except Exception as e:
        raise HTTPException(500, f"Rename failed on disk: {str(e)}")

    conn = db()
    cur = conn.cursor()
    try:
        cur.execute(
            "UPDATE files SET path = ?, name = ?, parent = ? WHERE path = ?",
            (
                str(new_path),
                name,
                str(new_path.parent),
                str(target),
            ),
        )

        # if folder → update all children paths
        if new_path.is_dir():
            old_prefix = str(target)
            new_prefix = str(new_path)

            cur.execute("SELECT path FROM files WHERE path LIKE ?", (old_prefix + "/%",))
            rows = cur.fetchall()

            for (child_path,) in rows:
                updated = child_path.replace(old_prefix, new_prefix, 1)
                cur.execute(
                    "UPDATE files SET path = ?, parent = ? WHERE path = ?",
                    (
                        updated,
                        str(Path(updated).parent),
                        child_path,
                    ),
                )
        conn.commit()
    except Exception as e:
        conn.rollback()
        # Rename back on disk
        try:
            new_path.rename(target)
        except Exception:
            pass
        raise HTTPException(500, f"Rename failed on database: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "old": str(target), "new": str(new_path)}


# Move a file or folder recursively to a new parent folder
@router.put("/move")
def move(path: str = Query(...), new_parent: str = Query(...)):
    src_path = safe_resolve(path)
    dst_parent = safe_resolve(new_parent)

    if not src_path.exists():
        raise HTTPException(404, "Source path not found")
    if src_path == RESOLVED_STORAGE_ROOT:
        raise HTTPException(400, "Cannot move storage root")
    if not dst_parent.exists() or not dst_parent.is_dir():
        raise HTTPException(404, "Destination folder not found")

    dst_path = safe_resolve(dst_parent / src_path.name)
    if dst_path.exists():
        raise HTTPException(400, "Item already exists at destination")

    # Perform filesystem move
    try:
        shutil.move(str(src_path), str(dst_path))
    except Exception as e:
        raise HTTPException(500, f"Move failed: {str(e)}")

    # Update database
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute(
            "UPDATE files SET path = ?, parent = ? WHERE path = ?",
            (str(dst_path), str(dst_parent), str(src_path)),
        )

        if dst_path.is_dir():
            old_prefix = str(src_path)
            new_prefix = str(dst_path)

            cur.execute("SELECT path FROM files WHERE path LIKE ?", (old_prefix + "/%",))
            rows = cur.fetchall()

            for (child_path,) in rows:
                updated = child_path.replace(old_prefix, new_prefix, 1)
                cur.execute(
                    "UPDATE files SET path = ?, parent = ? WHERE path = ?",
                    (
                        updated,
                        str(Path(updated).parent),
                        child_path,
                    ),
                )
        conn.commit()
    except Exception as e:
        conn.rollback()
        # Move back on disk
        try:
            shutil.move(str(dst_path), str(src_path))
        except Exception:
            pass
        raise HTTPException(500, f"Database update failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "old": str(src_path), "new": str(dst_path)}


# Copy files and folders recursively
@router.post("/copy")
def copy(path: str = Query(...), new_parent: str = Query(...)):
    src_path = safe_resolve(path)
    dst_parent = safe_resolve(new_parent)

    if not src_path.exists():
        raise HTTPException(404, "Source path not found")
    if src_path == RESOLVED_STORAGE_ROOT:
        raise HTTPException(400, "Cannot copy storage root")
    if not dst_parent.exists() or not dst_parent.is_dir():
        raise HTTPException(404, "Destination folder not found")

    dst_path = safe_resolve(dst_parent / src_path.name)
    if dst_path.exists():
        raise HTTPException(400, "Item already exists at destination")

    # Perform filesystem copy
    try:
        if src_path.is_file():
            shutil.copy2(src_path, dst_path)
        else:
            shutil.copytree(src_path, dst_path)
    except Exception as e:
        raise HTTPException(500, f"Copy failed: {str(e)}")

    # Update database
    conn = db()
    cur = conn.cursor()
    try:
        if src_path.is_file():
            cur.execute("SELECT size, owner_user_id, sha256 FROM files WHERE path = ?", (str(src_path),))
            row = cur.fetchone()
            size = row[0] if row else src_path.stat().st_size
            owner_id = row[1] if row else 1
            sha256 = row[2] if row else None

            cur.execute(
                """
                INSERT INTO files (path, name, parent, is_file, size, modified, created, favorite, owner_user_id, sha256, trashed)
                VALUES (?, ?, ?, 1, ?, ?, ?, 0, ?, ?, 0)
                """,
                (str(dst_path), dst_path.name, str(dst_parent), size, time.time(), time.time(), owner_id, sha256)
            )
        else:
            prefix = str(src_path)
            cur.execute(
                "SELECT path, parent, name, is_file, size, owner_user_id, sha256 FROM files WHERE path = ? OR path LIKE ?",
                (prefix, prefix + "/%")
            )
            rows = cur.fetchall()
            for r_path, r_parent, r_name, r_is_file, r_size, r_owner_id, r_sha256 in rows:
                assert r_path.startswith(prefix)
                new_r_path = r_path.replace(prefix, str(dst_path), 1)
                new_r_parent = r_parent.replace(prefix, str(dst_path), 1) if r_parent.startswith(prefix) else str(dst_parent)

                cur.execute(
                    """
                    INSERT INTO files (path, name, parent, is_file, size, modified, created, favorite, owner_user_id, sha256, trashed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0)
                    """,
                    (new_r_path, r_name, new_r_parent, r_is_file, r_size, time.time(), time.time(), r_owner_id, r_sha256)
                )
        conn.commit()
    except Exception as e:
        conn.rollback()
        # Clean up copied files on disk
        try:
            if dst_path.is_file():
                dst_path.unlink()
            else:
                shutil.rmtree(dst_path)
        except Exception:
            pass
        raise HTTPException(500, f"Database update failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "source": str(src_path), "copy": str(dst_path)}


# Restore a trashed file or folder
@router.post("/restore")
def restore(path: str = Query(...)):
    target = safe_resolve(path)

    conn = db()
    cur = conn.cursor()
    try:
        # Check if it exists in DB
        cur.execute("SELECT path, is_file FROM files WHERE path = ?", (str(target),))
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "Path not found in database")

        # Set trashed = 0 and trashed_at = NULL for the item and all its descendants
        cur.execute(
            "UPDATE files SET trashed = 0, trashed_at = NULL WHERE path = ? OR path LIKE ?",
            (str(target), str(target) + "/%")
        )

        # Recursively restore parent folders if they are trashed
        p = Path(target)
        while str(p) != str(RESOLVED_STORAGE_ROOT):
            cur.execute("UPDATE files SET trashed = 0, trashed_at = NULL WHERE path = ?", (str(p),))
            p = p.parent

        conn.commit()
    except Exception as e:
        conn.rollback()
        if isinstance(e, HTTPException):
            raise e
        raise HTTPException(500, f"Restore failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "restored": path}


# Empty all trashed items
@router.delete("/trash/empty")
def empty_trash(request: Request):
    from bifrost.audit import log_audit
    user_id = request.state.user_id if hasattr(request.state, "user_id") else None
    ip = request.client.host if request.client else "unknown"

    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT id, path, is_file FROM files WHERE trashed = 1")
        rows = cur.fetchall()
        file_ids = []
        for fid, path_str, is_file in rows:
            p = Path(path_str)
            if p.exists():
                if is_file:
                    try:
                        p.unlink()
                    except Exception:
                        pass
                else:
                    try:
                        shutil.rmtree(p)
                    except Exception:
                        pass
            file_ids.append(fid)
        cur.execute("DELETE FROM files WHERE trashed = 1")
        if file_ids:
            delete_chromadb_embeddings(file_ids)
        conn.commit()
        log_audit(user_id, "empty_trash", "all_trashed_items", ip, "success")
    except Exception as e:
        conn.rollback()
        log_audit(user_id, "empty_trash", "all_trashed_items", ip, "failed")
        raise HTTPException(500, f"Empty trash failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "message": "Trash emptied"}


# Favorite: Add PUT /mimir/favorite toggle
@router.put("/favorite")
def toggle_favorite(path: str = Query(...)):
    target = safe_resolve(path)
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT favorite FROM files WHERE path = ?", (str(target),))
        row = cur.fetchone()
        if not row:
            raise HTTPException(404, "Path not found")
        new_val = 0 if row[0] else 1
        cur.execute("UPDATE files SET favorite = ? WHERE path = ?", (new_val, str(target)))
        conn.commit()
    except Exception as e:
        conn.rollback()
        if isinstance(e, HTTPException):
            raise e
        raise HTTPException(500, f"Toggle favorite failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "path": path, "favorite": bool(new_val)}


# Initialize chunked upload session
@router.post("/upload/init")
def upload_init(
    filename: str = Query(...),
    path: str = Query("/"),
    size: int = Query(None),
    sha256: str = Query(None)
):
    TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    session_id = uuid.uuid4().hex
    upload_sessions[session_id] = {
        "filename": filename,
        "destination_path": path,
        "size": size,
        "sha256": sha256,
        "chunks": set()
    }
    return {"session_id": session_id}


# Upload chunk bytes
@router.post("/upload/chunk")
async def upload_chunk(
    request: Request,
    session_id: str = Query(...),
    chunk_index: int = Query(...)
):
    try:
        TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        if session_id not in upload_sessions:
            raise HTTPException(400, f"Invalid session ID. Available: {list(upload_sessions.keys())}")

        chunk_bytes = await request.body()

        # Write chunk bytes to temp file
        chunk_file = TEMP_UPLOAD_DIR / f"{session_id}_{chunk_index}"
        chunk_file.write_bytes(chunk_bytes)

        upload_sessions[session_id]["chunks"].add(chunk_index)
        return {"status": "ok", "chunk_index": chunk_index}
    except Exception as e:
        import traceback
        traceback.print_exc()
        if isinstance(e, HTTPException):
            raise e
        raise HTTPException(500, f"Failed to upload chunk: {str(e)}")


# Complete chunked upload session and verify size / hash
@router.post("/upload/complete")
def upload_complete(session_id: str = Query(...)):
    if session_id not in upload_sessions:
        raise HTTPException(400, "Invalid session ID")

    session = upload_sessions[session_id]
    filename = session["filename"]
    dest_path = session["destination_path"]
    expected_size = session["size"]
    expected_sha = session["sha256"]
    chunks = session["chunks"]

    if not chunks:
        raise HTTPException(400, "No chunks uploaded")

    # Resolve target
    folder = safe_resolve(dest_path)
    folder.mkdir(parents=True, exist_ok=True)
    save_path = safe_resolve(folder / filename)

    if save_path.exists():
        # Clean up chunk files first
        for idx in chunks:
            chunk_file = TEMP_UPLOAD_DIR / f"{session_id}_{idx}"
            if chunk_file.exists():
                chunk_file.unlink()
        upload_sessions.pop(session_id, None)
        raise HTTPException(400, "File already exists")

    # Reassemble chunks
    sorted_chunk_indices = sorted(chunks)
    try:
        with open(save_path, "wb") as outfile:
            for idx in sorted_chunk_indices:
                chunk_file = TEMP_UPLOAD_DIR / f"{session_id}_{idx}"
                if not chunk_file.exists():
                    raise HTTPException(400, f"Missing chunk index {idx}")
                outfile.write(chunk_file.read_bytes())
    except Exception as e:
        if save_path.exists():
            save_path.unlink()
        raise HTTPException(500, f"Error assembling file: {str(e)}")
    finally:
        # Clean up chunk files
        for idx in chunks:
            chunk_file = TEMP_UPLOAD_DIR / f"{session_id}_{idx}"
            if chunk_file.exists():
                chunk_file.unlink()
        # Remove from sessions
        upload_sessions.pop(session_id, None)

    # Check size
    actual_size = save_path.stat().st_size
    if expected_size is not None and actual_size != expected_size:
        if save_path.exists():
            save_path.unlink()
        raise HTTPException(400, "Size mismatch")

    # Calculate SHA-256
    actual_sha = calculate_sha256(save_path)
    if expected_sha is not None and actual_sha.lower() != expected_sha.lower():
        if save_path.exists():
            save_path.unlink()
        raise HTTPException(400, "Integrity check (SHA-256) failed")

    # Deduplication
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT path FROM files WHERE sha256 = ? AND is_file = 1 AND (trashed = 0 OR trashed IS NULL) LIMIT 1", (actual_sha,))
        row = cur.fetchone()
        if row:
            existing_path = Path(row[0])
            if existing_path.exists() and existing_path != save_path:
                save_path.unlink()
                os.link(existing_path, save_path)

        # Insert database record
        cur.execute(
            """
            INSERT OR IGNORE INTO files (path, name, parent, is_file, size, modified, created, favorite, sha256, trashed)
            VALUES (?, ?, ?, 1, ?, ?, ?, 0, ?, 0)
            """,
            (
                str(save_path),
                filename,
                str(folder),
                actual_size,
                time.time(),
                time.time(),
                actual_sha,
            ),
        )
        conn.commit()
    except Exception as e:
        conn.rollback()
        if save_path.exists():
            save_path.unlink()
        raise HTTPException(500, f"Database transaction failed: {str(e)}")
    finally:
        conn.close()

    return {"status": "ok", "saved_as": filename, "sha256": actual_sha}


# Search files via FTS5 index (with fallback to LIKE query)
@router.get("/search")
def search(q: str = Query(..., description="Search query")):
    conn = db()
    cur = conn.cursor()
    try:
        # FTS5 search (match prefixes of alphanumeric terms)
        cleaned_q = "".join(c for c in q if c.isalnum() or c.isspace() or c in ".-_")
        terms = [f"{t}*" for t in cleaned_q.split() if t]
        if terms:
            fts_query = " ".join(terms)
            cur.execute(
                """
                SELECT f.id, f.path, f.name, f.parent, f.is_file, f.size, f.modified, f.created, f.favorite
                FROM files f
                JOIN files_fts fts ON f.id = fts.file_id
                WHERE files_fts MATCH ? AND (f.trashed = 0 OR f.trashed IS NULL)
                """,
                (fts_query,)
            )
            rows = cur.fetchall()
        else:
            rows = []
    except sqlite3.OperationalError:
        # Fallback to simple LIKE search
        cur.execute(
            """
            SELECT id, path, name, parent, is_file, size, modified, created, favorite
            FROM files
            WHERE (name LIKE ? OR path LIKE ?) AND (trashed = 0 OR trashed IS NULL)
            """,
            (f"%{q}%", f"%{q}%")
        )
        rows = cur.fetchall()
    finally:
        conn.close()

    data = [
        {
            "id": r[0],
            "path": r[1],
            "name": r[2],
            "parent": r[3],
            "is_file": bool(r[4]),
            "size": r[5],
            "modified": r[6],
            "created": r[7],
            "favorite": bool(r[8]),
        }
        for r in rows
    ]
    return {"data": data}


# Get quota usage of files
@router.get("/usage")
def get_usage():
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT SUM(size) FROM files WHERE is_file = 1 AND (trashed = 0 OR trashed IS NULL)")
    row = cur.fetchone()
    conn.close()
    total_size = row[0] if row[0] is not None else 0
    return {"total_usage_bytes": total_size}
