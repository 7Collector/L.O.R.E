import os
import time
import json
import sqlite3
import shutil
import pytest
from pathlib import Path
from fastapi.testclient import TestClient
from PIL import Image
from io import BytesIO

# Configure environment variables for tests
os.environ["API_KEY"] = "test-secret-key"
os.environ["LORE_BASE_DIR"] = "/tmp/lore_security_test_data"
os.environ["LORE_DB_PATH"] = "/tmp/lore_security_test_data/saraswati_db"
os.environ["LORE_PSYCHE_PATH"] = "/tmp/lore_security_test_data/psyche_db"
os.environ["ALLOWED_ORIGINS"] = "http://myclient.local,http://localhost:8000"

import constants
import bifrost.main
import bifrost.auth
import bifrost.audit
import mimir.router
import mimir.saraswati.init
import heimdall.router

from constants import DB_PATH, BASE_DIR
from bifrost.main import app
from mimir.saraswati.init import run_migrations
from bifrost.auth import create_magic_token, hash_token

@pytest.fixture(scope="module", autouse=True)
def isolate_db_path():
    # Save original values
    orig_constants = constants.DB_PATH
    orig_auth = bifrost.auth.DB_PATH
    orig_audit = bifrost.audit.DB_PATH
    orig_mimir = mimir.router.DB_PATH
    orig_init = mimir.saraswati.init.DB_PATH
    orig_heimdall = heimdall.router.DB_PATH

    # Set security test database path
    new_db = Path("/tmp/lore_security_test_data/saraswati_db")

    # Apply patches
    constants.DB_PATH = new_db
    bifrost.auth.DB_PATH = new_db
    bifrost.audit.DB_PATH = new_db
    mimir.router.DB_PATH = new_db
    mimir.saraswati.init.DB_PATH = new_db
    heimdall.router.DB_PATH = new_db

    # Clean directories
    shutil.rmtree("/tmp/lore_security_test_data", ignore_errors=True)
    Path("/tmp/lore_security_test_data").mkdir(parents=True, exist_ok=True)

    yield

    # Restore original values
    constants.DB_PATH = orig_constants
    bifrost.auth.DB_PATH = orig_auth
    bifrost.audit.DB_PATH = orig_audit
    mimir.router.DB_PATH = orig_mimir
    mimir.saraswati.init.DB_PATH = orig_init
    heimdall.router.DB_PATH = orig_heimdall


@pytest.fixture(autouse=True)
def setup_test_db():
    base_dir = Path("/tmp/lore_security_test_data")
    base_dir.mkdir(parents=True, exist_ok=True)

    files_dir = base_dir / "files"
    if files_dir.exists():
        try:
            shutil.rmtree(files_dir)
        except Exception:
            pass
    files_dir.mkdir(parents=True, exist_ok=True)
    
    db_file = Path("/tmp/lore_security_test_data/saraswati_db")
    if db_file.exists():
        try:
            db_file.unlink()
        except Exception:
            pass

    # Run migrations to create clean DB structure in the isolated test database
    run_migrations()
    yield
    
    # Cleanup files
    if files_dir.exists():
        try:
            shutil.rmtree(files_dir)
        except Exception:
            pass


def test_security_headers():
    client = TestClient(app)
    res = client.get("/")
    assert res.status_code == 200
    assert res.headers["X-Content-Type-Options"] == "nosniff"
    assert res.headers["X-Frame-Options"] == "DENY"
    assert "Strict-Transport-Security" in res.headers
    assert "Content-Security-Policy" in res.headers


def test_cors_origins():
    client = TestClient(app)
    headers = {"Origin": "http://myclient.local"}
    res = client.options("/", headers=headers)
    assert res.headers.get("access-control-allow-origin") == "http://myclient.local"
    
    headers = {"Origin": "http://unallowed.com"}
    res = client.options("/", headers=headers)
    assert res.headers.get("access-control-allow-origin") is None


def test_rate_limiting():
    from bifrost.main import RATE_LIMIT_STORE
    RATE_LIMIT_STORE.clear()

    client = TestClient(app)
    payload = {"email": "test@lore.local"}
    for i in range(12):
        res = client.post("/auth/request-link", json=payload)
        if i >= 10:
            assert res.status_code == 429
            
            conn = sqlite3.connect(constants.DB_PATH)
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) FROM audit_logs WHERE action = 'rate_limit_exceeded'")
            count = cur.fetchone()[0]
            conn.close()
            assert count > 0
            break


def test_audit_logging():
    from bifrost.main import RATE_LIMIT_STORE
    RATE_LIMIT_STORE.clear()

    client = TestClient(app)
    client.get("/auth/verify?token=invalid_token")
    
    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT * FROM audit_logs")
    all_logs = cur.fetchall()
    print("ALL AUDIT LOGS IN SECURITY DB:", all_logs)
    
    cur.execute("SELECT action, status FROM audit_logs WHERE action = 'auth_login' ORDER BY id DESC")
    row = cur.fetchone()
    conn.close()
    
    assert row is not None
    assert row[1] == "failed"


def test_content_disposition():
    client = TestClient(app)
    headers = {"X-Api-Key": "test-secret-key"}
    
    # 1. Create HTML file (non-preview)
    html_bytes = b"<html><body>Hello</body></html>"
    files = {"file": ("danger.html", html_bytes, "text/html")}
    res = client.post("/mimir/upload?path=/", files=files, headers=headers)
    assert res.status_code == 200
    
    # Retrieve file ID
    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'danger.html'")
    html_id = cur.fetchone()[0]
    conn.close()

    # 2. Create image file (preview allowed)
    im = Image.new("RGB", (10, 10), color="blue")
    buf = BytesIO()
    im.save(buf, format="JPEG")
    img_bytes = buf.getvalue()
    files = {"file": ("safe.jpg", img_bytes, "image/jpeg")}
    res = client.post("/mimir/upload?path=/", files=files, headers=headers)
    assert res.status_code == 200

    # Retrieve image file ID
    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'safe.jpg'")
    jpg_id = cur.fetchone()[0]
    conn.close()

    # Create share link for the HTML file
    payload = {
        "resource_type": "file",
        "resource_id": html_id,
        "permission": "view"
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    html_token = res.json()["token"]

    # Verify view endpoint forces Content-Disposition attachment for HTML
    res = client.get(f"/s/{html_token}/view/{html_id}")
    assert res.status_code == 200
    assert "attachment" in res.headers.get("content-disposition", "")

    # Create share link for the JPEG image file
    payload = {
        "resource_type": "file",
        "resource_id": jpg_id,
        "permission": "view"
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    jpg_token = res.json()["token"]

    # Verify view endpoint does NOT force attachment for JPEG (renders inline)
    res = client.get(f"/s/{jpg_token}/view/{jpg_id}")
    assert res.status_code == 200
    assert "attachment" not in res.headers.get("content-disposition", "")


def test_sharing_flow():
    client = TestClient(app)
    headers = {"X-Api-Key": "test-secret-key"}

    files = {"file": ("doc.pdf", b"%PDF-1.4 dummy contents", "application/pdf")}
    res = client.post("/mimir/upload?path=/", files=files, headers=headers)
    assert res.status_code == 200

    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'doc.pdf'")
    file_id = cur.fetchone()[0]
    conn.close()

    payload = {
        "resource_type": "file",
        "resource_id": file_id,
        "permission": "view",
        "max_uses": 2
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    assert res.status_code == 200
    share_data = res.json()
    token = share_data["token"]
    
    # Access 1 (without cookies) -> increment use_count to 1
    res1 = client.get(f"/s/{token}")
    assert res1.status_code in [200, 303]
    
    # Access 2 (without cookies) -> increment use_count to 2
    res2 = client.get(f"/s/{token}")
    assert res2.status_code in [200, 303]
    
    # Access 3 (without cookies) -> should return 403 (overused)
    res3 = client.get(f"/s/{token}")
    assert res3.status_code == 403


def test_password_gated_share():
    client = TestClient(app)
    headers = {"X-Api-Key": "test-secret-key"}

    files = {"file": ("secret.txt", b"classified", "text/plain")}
    client.post("/mimir/upload?path=/", files=files, headers=headers)
    
    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'secret.txt'")
    file_id = cur.fetchone()[0]
    conn.close()

    payload = {
        "resource_type": "file",
        "resource_id": file_id,
        "password": "mypassword"
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    token = res.json()["token"]

    res = client.get(f"/s/{token}")
    assert "Password Required" in res.text

    res = client.get(f"/s/{token}/file-preview/{file_id}")
    assert res.status_code == 403

    res = client.post(f"/s/{token}/verify-password", data={"password": "wrongpassword"})
    assert res.status_code == 403

    res = client.post(f"/s/{token}/verify-password", data={"password": "mypassword"})
    assert res.status_code in [200, 303]
    cookies = res.cookies

    res = client.get(f"/s/{token}/file-preview/{file_id}", cookies=cookies)
    assert res.status_code == 200
    assert "secret.txt" in res.text


def test_email_gated_share():
    client = TestClient(app)
    headers = {"X-Api-Key": "test-secret-key"}

    files = {"file": ("friend_only.pdf", b"hello friend", "application/pdf")}
    client.post("/mimir/upload?path=/", files=files, headers=headers)

    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'friend_only.pdf'")
    file_id = cur.fetchone()[0]
    conn.close()

    payload = {
        "resource_type": "file",
        "resource_id": file_id,
        "requires_email": True,
        "allowed_emails": ["allowed@example.com"]
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    token = res.json()["token"]

    res = client.get(f"/s/{token}")
    assert "Email Verification Required" in res.text

    res = client.post(f"/s/{token}/request-email-link", data={"email": "hacker@example.com"})
    assert res.status_code == 403

    res = client.post(f"/s/{token}/request-email-link", data={"email": "allowed@example.com"})
    assert res.status_code == 200

    magic_token = create_magic_token("allowed@example.com")
    import jwt
    from bifrost.auth import JWT_SECRET, JWT_ALGORITHM
    magic_payload = {
        "email": "allowed@example.com",
        "token": token,
        "exp": time.time() + 900
    }
    custom_magic_token = jwt.encode(magic_payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

    res = client.get(f"/s/{token}/verify-email?token={custom_magic_token}")
    assert res.status_code in [200, 303]
    cookies = res.cookies

    res = client.get(f"/s/{token}/file-preview/{file_id}", cookies=cookies)
    assert res.status_code == 200
    assert "friend_only.pdf" in res.text


def test_folder_traversal_gated_share():
    client = TestClient(app)
    headers = {"X-Api-Key": "test-secret-key"}

    res = client.post("/mimir/create_folder?path=/&name=shared_folder", headers=headers)
    assert res.status_code == 200

    files = {"file": ("inside.txt", b"inside", "text/plain")}
    res = client.post("/mimir/upload?path=/shared_folder", files=files, headers=headers)
    assert res.status_code == 200

    files = {"file": ("outside.txt", b"outside", "text/plain")}
    res = client.post("/mimir/upload?path=/", files=files, headers=headers)
    assert res.status_code == 200

    conn = sqlite3.connect(constants.DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM files WHERE name = 'shared_folder'")
    folder_id = cur.fetchone()[0]
    cur.execute("SELECT id FROM files WHERE name = 'inside.txt'")
    inside_id = cur.fetchone()[0]
    cur.execute("SELECT id FROM files WHERE name = 'outside.txt'")
    outside_id = cur.fetchone()[0]
    conn.close()

    payload = {
        "resource_type": "folder",
        "resource_id": folder_id
    }
    res = client.post("/mimir/shares", json=payload, headers=headers)
    token = res.json()["token"]

    res = client.get(f"/s/{token}/list/{folder_id}")
    assert res.status_code == 200
    assert "inside.txt" in [item["name"] for item in res.json()["data"]]

    res = client.get(f"/s/{token}/download/{inside_id}")
    assert res.status_code == 200

    res = client.get(f"/s/{token}/download/{outside_id}")
    assert res.status_code == 403
