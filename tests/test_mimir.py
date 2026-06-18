import os
import time
import sqlite3
import shutil
import hashlib
import zipfile
import pytest
from io import BytesIO
from pathlib import Path
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

def get_bytes_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

@pytest.fixture(autouse=True)
def setup_test_db():
    # Clean up test directories and database
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

def get_headers():
    return {"X-Api-Key": "test-secret-key"}

def test_safety_traversal_guard():
    client = TestClient(app)
    headers = get_headers()
    
    # Try downloading with traversal path
    res = client.get("/mimir/download/..%2F..%2Fetc%2Fpasswd", headers=headers)
    assert res.status_code == 400
    assert "Directory traversal" in res.text
    
    # Try listing outside storage root
    res = client.get("/mimir/list?path=../../", headers=headers)
    assert res.status_code == 400
    
    # Try creating folder outside
    res = client.post("/mimir/create_folder?path=../../&name=bad", headers=headers)
    assert res.status_code == 400

def test_move_vs_rename():
    client = TestClient(app)
    headers = get_headers()

    # 1. Create folders
    res = client.post("/mimir/create_folder?path=/&name=dir1", headers=headers)
    assert res.status_code == 200
    res = client.post("/mimir/create_folder?path=/&name=dir2", headers=headers)
    assert res.status_code == 200

    # 2. Upload file to /dir1
    content = b"hello world"
    files = {"file": ("test.txt", content, "text/plain")}
    res = client.post("/mimir/upload?path=/dir1", files=files, headers=headers)
    assert res.status_code == 200

    # 3. Rename file
    res = client.put("/mimir/rename?path=/dir1/test.txt&name=test_renamed.txt", headers=headers)
    assert res.status_code == 200
    
    # Verify file is renamed in DB
    res = client.get("/mimir/list?path=/dir1", headers=headers)
    assert res.status_code == 200
    data = res.json()["data"]
    assert len(data) == 1
    assert data[0]["name"] == "test_renamed.txt"

    # Rename folder /dir1 to /dir1_renamed
    res = client.put("/mimir/rename?path=/dir1&name=dir1_renamed", headers=headers)
    assert res.status_code == 200

    # Verify children paths updated recursively in DB
    res = client.get("/mimir/list?path=/dir1_renamed", headers=headers)
    assert res.status_code == 200
    data = res.json()["data"]
    assert len(data) == 1
    assert data[0]["name"] == "test_renamed.txt"

    # Move file test_renamed.txt to /dir2
    res = client.put("/mimir/move?path=/dir1_renamed/test_renamed.txt&new_parent=/dir2", headers=headers)
    assert res.status_code == 200

    # Verify moved
    res = client.get("/mimir/list?path=/dir1_renamed", headers=headers)
    assert len(res.json()["data"]) == 0
    res = client.get("/mimir/list?path=/dir2", headers=headers)
    assert len(res.json()["data"]) == 1
    assert res.json()["data"][0]["name"] == "test_renamed.txt"

    # Move directory /dir1_renamed to /dir2
    res = client.put("/mimir/move?path=/dir1_renamed&new_parent=/dir2", headers=headers)
    assert res.status_code == 200

    # Verify moved and nested file path updated
    res = client.get("/mimir/list?path=/dir2/dir1_renamed", headers=headers)
    assert res.status_code == 200

def test_copy():
    client = TestClient(app)
    headers = get_headers()

    # Create folders
    client.post("/mimir/create_folder?path=/&name=folderA", headers=headers)
    client.post("/mimir/create_folder?path=/&name=folderB", headers=headers)

    # Upload file
    content = b"copy content"
    files = {"file": ("fileA.txt", content, "text/plain")}
    client.post("/mimir/upload?path=/folderA", files=files, headers=headers)

    # Copy file to folderB
    res = client.post("/mimir/copy?path=/folderA/fileA.txt&new_parent=/folderB", headers=headers)
    assert res.status_code == 200

    # Verify original and copy exist
    res1 = client.get("/mimir/list?path=/folderA", headers=headers)
    assert len(res1.json()["data"]) == 1
    res2 = client.get("/mimir/list?path=/folderB", headers=headers)
    assert len(res2.json()["data"]) == 1
    assert res2.json()["data"][0]["name"] == "fileA.txt"

    # Copy folderB recursively to folderA (folderB contains fileA.txt)
    res = client.post("/mimir/copy?path=/folderB&new_parent=/folderA", headers=headers)
    assert res.status_code == 200

    # Verify folderB was copied under folderA and contains the nested file
    res3 = client.get("/mimir/list?path=/folderA/folderB", headers=headers)
    assert res3.status_code == 200
    assert len(res3.json()["data"]) == 1
    assert res3.json()["data"][0]["name"] == "fileA.txt"

def test_favorite():
    client = TestClient(app)
    headers = get_headers()

    # Upload file
    files = {"file": ("fav.txt", b"some content", "text/plain")}
    client.post("/mimir/upload?path=/", files=files, headers=headers)

    # Verify not favorited by default
    res = client.get("/mimir/list?path=/", headers=headers)
    assert res.json()["data"][0]["favorite"] is False

    # Toggle favorite
    res = client.put("/mimir/favorite?path=/fav.txt", headers=headers)
    assert res.status_code == 200
    assert res.json()["favorite"] is True

    res = client.get("/mimir/list?path=/", headers=headers)
    assert res.json()["data"][0]["favorite"] is True

    # Toggle favorite off
    res = client.put("/mimir/favorite?path=/fav.txt", headers=headers)
    assert res.status_code == 200
    assert res.json()["favorite"] is False

def test_trash_and_restore():
    client = TestClient(app)
    headers = get_headers()

    # Create folder and file
    client.post("/mimir/create_folder?path=/&name=trashdir", headers=headers)
    files = {"file": ("trashfile.txt", b"to be trashed", "text/plain")}
    client.post("/mimir/upload?path=/trashdir", files=files, headers=headers)

    # 1. Soft delete
    res = client.delete("/mimir/delete?path=/trashdir/trashfile.txt", headers=headers)
    assert res.status_code == 200
    assert res.json()["permanent"] is False

    # Verify hidden from listing
    res = client.get("/mimir/list?path=/trashdir", headers=headers)
    assert len(res.json()["data"]) == 0

    # 2. Restore file
    res = client.post("/mimir/restore?path=/trashdir/trashfile.txt", headers=headers)
    assert res.status_code == 200

    # Verify visible again
    res = client.get("/mimir/list?path=/trashdir", headers=headers)
    assert len(res.json()["data"]) == 1

    # 3. Soft delete the folder
    client.delete("/mimir/delete?path=/trashdir", headers=headers)
    
    # Verify both folder and file are hidden
    res = client.get("/mimir/list?path=/", headers=headers)
    assert len(res.json()["data"]) == 0

    # Restore the nested file (should recursively restore parent folder too)
    res = client.post("/mimir/restore?path=/trashdir/trashfile.txt", headers=headers)
    assert res.status_code == 200

    # Verify parent folder and file are visible again
    res = client.get("/mimir/list?path=/", headers=headers)
    assert len(res.json()["data"]) == 1
    assert res.json()["data"][0]["name"] == "trashdir"

    res = client.get("/mimir/list?path=/trashdir", headers=headers)
    assert len(res.json()["data"]) == 1

    # 4. Empty trash
    # Trash file again
    client.delete("/mimir/delete?path=/trashdir/trashfile.txt", headers=headers)
    # Empty trash
    res = client.delete("/mimir/trash/empty", headers=headers)
    assert res.status_code == 200
    
    # Verify file is permanently gone (not even in DB for restore)
    res = client.post("/mimir/restore?path=/trashdir/trashfile.txt", headers=headers)
    assert res.status_code == 404

def test_chunked_upload():
    client = TestClient(app)
    headers = get_headers()

    content = b"chunk1chunk2chunk3"
    sha = get_bytes_sha256(content)

    # 1. Initialize
    res = client.post(
        f"/mimir/upload/init?filename=chunked.txt&path=/&size={len(content)}&sha256={sha}",
        headers=headers
    )
    assert res.status_code == 200
    session_id = res.json()["session_id"]
    assert session_id

    # 2. Upload chunks (out of order!)
    res = client.post(
        f"/mimir/upload/chunk?session_id={session_id}&chunk_index=1",
        content=b"chunk2",
        headers=headers
    )
    assert res.status_code == 200

    res = client.post(
        f"/mimir/upload/chunk?session_id={session_id}&chunk_index=0",
        content=b"chunk1",
        headers=headers
    )
    assert res.status_code == 200

    res = client.post(
        f"/mimir/upload/chunk?session_id={session_id}&chunk_index=2",
        content=b"chunk3",
        headers=headers
    )
    assert res.status_code == 200

    # 3. Complete upload
    res = client.post(f"/mimir/upload/complete?session_id={session_id}", headers=headers)
    assert res.status_code == 200
    assert res.json()["saved_as"] == "chunked.txt"
    assert res.json()["sha256"] == sha

    # Verify content
    res = client.get("/mimir/list?path=/", headers=headers)
    assert len(res.json()["data"]) == 1
    file_id = res.json()["data"][0]["id"]

    res = client.get(f"/mimir/download/{file_id}", headers=headers)
    assert res.status_code == 200
    assert res.content == content

    # Test chunked upload size mismatch error
    res = client.post(f"/mimir/upload/init?filename=badsize.txt&path=/&size=9999", headers=headers)
    sid = res.json()["session_id"]
    client.post(f"/mimir/upload/chunk?session_id={sid}&chunk_index=0", content=b"chunk", headers=headers)
    res = client.post(f"/mimir/upload/complete?session_id={sid}", headers=headers)
    assert res.status_code == 400
    assert "size mismatch" in res.text.lower()

def test_download():
    client = TestClient(app)
    headers = get_headers()

    # Create folder and file
    client.post("/mimir/create_folder?path=/&name=downfolder", headers=headers)
    content = b"download test content"
    files = {"file": ("downfile.txt", content, "text/plain")}
    client.post("/mimir/upload?path=/downfolder", files=files, headers=headers)

    res_list = client.get("/mimir/list?path=/downfolder", headers=headers)
    file_id = res_list.json()["data"][0]["id"]

    # 1. Download file by path
    res = client.get("/mimir/download/downfolder/downfile.txt", headers=headers)
    assert res.status_code == 200
    assert res.content == content

    # 2. Download file by ID
    res = client.get(f"/mimir/download/{file_id}", headers=headers)
    assert res.status_code == 200
    assert res.content == content
    assert "attachment" in res.headers.get("content-disposition", "")
    assert "downfile.txt" in res.headers.get("content-disposition", "")

    # 3. Download folder as ZIP
    res_list_root = client.get("/mimir/list?path=/", headers=headers)
    folder_id = [item["id"] for item in res_list_root.json()["data"] if item["name"] == "downfolder"][0]

    res = client.get(f"/mimir/download/{folder_id}", headers=headers)
    assert res.status_code == 200
    assert "zip" in res.headers.get("content-type", "")

    # Read zip content
    zip_bytes = BytesIO(res.content)
    with zipfile.ZipFile(zip_bytes) as zf:
        namelist = zf.namelist()
        assert "downfile.txt" in namelist
        assert zf.read("downfile.txt") == content

def test_quota_and_search_and_deduplication():
    client = TestClient(app)
    headers = get_headers()

    # Upload unique file A
    content_a = b"AAA"
    sha_a = get_bytes_sha256(content_a)
    client.post("/mimir/upload?path=/", files={"file": ("fileA.txt", content_a, "text/plain")}, headers=headers)

    # Upload identical file B (deduplication check)
    client.post("/mimir/upload?path=/", files={"file": ("fileB.txt", content_a, "text/plain")}, headers=headers)

    # Verify hard linked (stat number of links >= 2 on same inode)
    import os
    from pathlib import Path
    test_dir = Path("/tmp/lore_test_data/files")
    path_a = test_dir / "fileA.txt"
    path_b = test_dir / "fileB.txt"
    assert path_a.exists()
    assert path_b.exists()
    # Check inodes match
    assert path_a.stat().st_ino == path_b.stat().st_ino

    # Verify quota usage (only counts active files; total_usage_bytes is the sum of sizes in database)
    res = client.get("/mimir/usage", headers=headers)
    assert res.status_code == 200
    assert res.json()["total_usage_bytes"] == 6

    # Trash one file and check usage
    client.delete("/mimir/delete?path=/fileB.txt", headers=headers)
    res = client.get("/mimir/usage", headers=headers)
    assert res.json()["total_usage_bytes"] == 3

    # FTS5 Search
    res = client.get("/mimir/search?q=fileA", headers=headers)
    assert res.status_code == 200
    assert len(res.json()["data"]) == 1
    assert res.json()["data"][0]["name"] == "fileA.txt"
