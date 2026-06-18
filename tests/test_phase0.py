import os
import time
import sqlite3
import pytest
from fastapi.testclient import TestClient

# Configure testing environment variables before importing main
os.environ["API_KEY"] = "test-secret-key"
os.environ["LORE_BASE_DIR"] = "/tmp/lore_test_data"
os.environ["LORE_DB_PATH"] = "/tmp/lore_test_data/saraswati_db"
os.environ["LORE_PSYCHE_PATH"] = "/tmp/lore_test_data/psyche_db"
os.environ["SMTP_HOST"] = "mock"

from constants import DB_PATH
from bifrost.main import app
from mimir.saraswati.init import run_migrations

@pytest.fixture(autouse=True)
def setup_test_db():
    # Clean up test directories and database
    import shutil
    from pathlib import Path
    base_dir = Path("/tmp/lore_test_data")
    base_dir.mkdir(parents=True, exist_ok=True)
    
    files_dir = base_dir / "files"
    if files_dir.exists():
        shutil.rmtree(files_dir)
    files_dir.mkdir(parents=True, exist_ok=True)
    
    db_file = base_dir / "saraswati_db"
    if db_file.exists():
        db_file.unlink()
    
    # Run migrations to create clean DB structure
    run_migrations()
    yield
    # Clean up files on teardown, but do not delete psyche_db directory
    if files_dir.exists():
        shutil.rmtree(files_dir)

def test_static_key_authentication():
    client = TestClient(app)
    # Without key
    res = client.get("/")
    assert res.status_code == 200  # Public endpoint
    
    res = client.get("/mimir/list")
    assert res.status_code == 401
    
    # With correct static key
    headers = {"X-Api-Key": "test-secret-key"}
    res = client.get("/mimir/list", headers=headers)
    # Since BASE_DIR/files directory won't exist or is empty, we might get a 404/422/etc. but not 401
    assert res.status_code != 401

def test_passwordless_magic_link_flow():
    client = TestClient(app)
    
    # 1. Request magic link for unregistered/uninvited email (first user auto-registration)
    payload = {"email": "owner@lore.local"}
    res = client.post("/auth/request-link", json=payload)
    assert res.status_code == 200
    assert res.json()["status"] == "ok"

    # Try requesting for another email (should fail as owner@lore.local is registered, so count > 0)
    payload = {"email": "stranger@lore.local"}
    res = client.post("/auth/request-link", json=payload)
    assert res.status_code == 403

    # But we can register another user directly in the database to invite them
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO users (email, display_name, is_owner, created) VALUES (?, ?, 0, ?)",
        ("invited@lore.local", "Invited User", time.time())
    )
    conn.commit()
    conn.close()

    # Now request link for invited user should succeed
    payload = {"email": "invited@lore.local"}
    res = client.post("/auth/request-link", json=payload)
    assert res.status_code == 200
    
    # Let's inspect the console-mock email delivery (via capturing logs or decoding the signed token directly)
    # Let's generate a valid token manually to simulate verification, or extract it.
    from bifrost.auth import create_magic_token
    token = create_magic_token("invited@lore.local")
    
    # 2. Verify magic link
    res = client.get(f"/auth/verify?token={token}&device_label=TestPhone")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert "session_token" in data
    assert data["user"]["email"] == "invited@lore.local"
    assert data["user"]["is_owner"] is False
    
    session_token = data["session_token"]
    
    # 3. Access protected route with Bearer Token
    headers = {"Authorization": f"Bearer {session_token}"}
    res = client.get("/mimir/list", headers=headers)
    assert res.status_code != 401
    
    # 4. Access with invalid Bearer Token
    headers_invalid = {"Authorization": "Bearer badtoken123"}
    res = client.get("/mimir/list", headers=headers_invalid)
    assert res.status_code == 401
