import os
import time
import hashlib
import secrets
import sqlite3
import jwt
from pathlib import Path
from constants import DB_PATH

JWT_SECRET = os.getenv("JWT_SECRET", "dev-secret-key-12345")
JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
JWT_EXPIRY_HOURS = int(os.getenv("JWT_EXPIRY_HOURS", "168"))

def db():
    return sqlite3.connect(DB_PATH)

def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()

def create_magic_token(email: str, expires_in: int = 900) -> str:
    """Creates a JWT token for the passwordless magic link (expires in 15 mins by default)"""
    payload = {
        "email": email.strip().lower(),
        "exp": time.time() + expires_in
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def decode_magic_token(token: str) -> str | None:
    """Decodes JWT magic token and returns email if valid, otherwise None"""
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        return payload.get("email")
    except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
        return None

def create_session(user_id: int, device_label: str = "Unknown Device") -> str:
    """Generates a new session token, stores its hash, and returns the plain text token"""
    token = secrets.token_hex(32)
    token_hash = hash_token(token)
    
    now = time.time()
    expires_at = now + (JWT_EXPIRY_HOURS * 3600)

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        INSERT INTO sessions (user_id, token_hash, device_label, created, expires_at, revoked)
        VALUES (?, ?, ?, ?, ?, 0)
        """,
        (user_id, token_hash, device_label, now, expires_at)
    )
    conn.commit()
    conn.close()
    return token

def verify_session_token(token: str) -> dict | None:
    """Hashes the session token and validates it against the sessions table"""
    token_hash = hash_token(token)
    now = time.time()

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT u.id, u.email, u.display_name, u.is_owner, s.expires_at, s.revoked
        FROM sessions s
        JOIN users u ON s.user_id = u.id
        WHERE s.token_hash = ?
        """,
        (token_hash,)
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return None

    user_id, email, display_name, is_owner, expires_at, revoked = row
    if revoked or expires_at < now:
        return None

    return {
        "id": user_id,
        "email": email,
        "display_name": display_name,
        "is_owner": bool(is_owner)
    }

def get_user_by_email(email: str) -> dict | None:
    conn = db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, email, display_name, is_owner FROM users WHERE email = ?",
        (email.strip().lower(),)
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return None

    return {
        "id": row[0],
        "email": row[1],
        "display_name": row[2],
        "is_owner": bool(row[3])
    }

def create_user(email: str, display_name: str, is_owner: bool = False) -> dict:
    conn = db()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO users (email, display_name, is_owner, created) VALUES (?, ?, ?, ?)",
        (email.strip().lower(), display_name, 1 if is_owner else 0, time.time())
    )
    user_id = cur.lastrowid
    conn.commit()
    conn.close()
    return {
        "id": user_id,
        "email": email,
        "display_name": display_name,
        "is_owner": is_owner
    }

import json
def check_and_create_share_guest_user(email: str) -> bool:
    """
    Checks if the given email is allowed by any active, non-expired, non-revoked
    email-gated share link. If allowed, creates a guest user account.
    """
    email = email.strip().lower()
    
    # If user already exists, return True
    user = get_user_by_email(email)
    if user:
        return True

    now = time.time()
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT sl.allowed_emails, sl.requires_email 
        FROM share_links sl
        JOIN shares s ON sl.share_id = s.id
        WHERE sl.revoked = 0 
          AND s.revoked = 0 
          AND (sl.expires_at IS NULL OR sl.expires_at > ?)
          AND (s.expires_at IS NULL OR s.expires_at > ?)
          AND (sl.max_uses IS NULL OR sl.use_count < sl.max_uses)
        """,
        (now, now)
    )
    rows = cur.fetchall()
    conn.close()

    allowed = False
    for allowed_emails_str, requires_email in rows:
        if requires_email:
            if not allowed_emails_str:
                # requires_email is true but allowed_emails is empty/null -> anyone verified is allowed
                allowed = True
                break
            try:
                emails_list = json.loads(allowed_emails_str)
                if isinstance(emails_list, list) and email in [e.strip().lower() for e in emails_list]:
                    allowed = True
                    break
            except Exception:
                pass

    if allowed:
        create_user(email, email.split("@")[0], is_owner=False)
        return True

    return False

