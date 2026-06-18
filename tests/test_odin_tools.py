import os
import time
import sqlite3
import pytest
from pathlib import Path
import json

# Configure testing environment variables before importing
os.environ["API_KEY"] = "test-secret-key"
os.environ["LORE_BASE_DIR"] = "/tmp/lore_test_data"
os.environ["LORE_DB_PATH"] = "/tmp/lore_test_data/saraswati_db"
os.environ["LORE_PSYCHE_PATH"] = "/tmp/lore_test_data/psyche_db"

from constants import DB_PATH, BASE_DIR
from mimir.saraswati.init import run_migrations

# Import the tools to test
from odin.tools.file_search import file_search
from odin.tools.vector_search import vector_search
from odin.tools.read_file import read_file
from odin.tools.describe_photo import describe_photo
from odin.tools.metadata import get_metadata

@pytest.fixture(autouse=True)
def setup_test_db():
    import shutil
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
    
    # Reset ChromaDB
    try:
        from psyche.init import client
        try:
            client.delete_collection("file_embeddings")
        except Exception:
            pass
        client.get_or_create_collection(
            name="file_embeddings",
            metadata={"hnsw:space": "cosine"}
        )
    except Exception as e:
        print(f"Error resetting collection: {e}")
        
    yield
    # Clean up files on teardown, but do not delete psyche_db directory
    if files_dir.exists():
        shutil.rmtree(files_dir)

def test_file_search_and_metadata():
    # Insert mock file into database
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Insert a dummy file
    cursor.execute("""
        INSERT INTO files (id, path, name, parent, is_file, size, modified, created, sha256, trashed)
        VALUES (42, '/tmp/lore_test_data/files/hello.txt', 'hello.txt', '/tmp/lore_test_data/files', 1, 100, 12345.0, 12345.0, 'dummy-sha256', 0)
    """)
    # Insert to files_fts (simulating trigger population or manual insert for test context)
    cursor.execute("INSERT OR IGNORE INTO files_fts(file_id, name, path) VALUES (42, 'hello.txt', '/tmp/lore_test_data/files/hello.txt')")
    
    conn.commit()
    conn.close()
    
    # Test file_search FTS MATCH
    res = file_search("hello")
    results = json.loads(res)
    assert len(results) > 0
    assert results[0]["name"] == "hello.txt"
    assert results[0]["file_id"] == 42
    
    # Test get_metadata
    meta_res = get_metadata("42")
    meta = json.loads(meta_res)
    assert meta["id"] == 42
    assert meta["name"] == "hello.txt"
    assert meta["sha256"] == "dummy-sha256"

def test_read_file_security():
    files_dir = Path(BASE_DIR) / "files"
    
    # 1. Create a valid text file
    valid_file = files_dir / "safe.txt"
    with open(valid_file, "w", encoding="utf-8") as f:
        f.write("Hello, safety!")
        
    # Register file in DB so read_file can resolve by file_id if needed
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO files (id, path, name, parent, is_file, size, modified, created)
        VALUES (101, ?, 'safe.txt', ?, 1, 14, 12345.0, 12345.0)
    """, (str(valid_file), str(files_dir)))
    conn.commit()
    conn.close()
    
    # Test valid read by ID
    content = read_file(file_id="101")
    assert content == "Hello, safety!"
    
    # Test valid read by path
    content = read_file(path=str(valid_file))
    assert content == "Hello, safety!"
    
    # 2. Path Traversal Check (relative path traversing up)
    traversal_path = files_dir / "../secret.txt"
    content = read_file(path=str(traversal_path))
    assert "Access Denied" in content or "outside" in content
    
    # 3. File size check (simulate large file)
    large_file = files_dir / "large.txt"
    with open(large_file, "wb") as f:
        f.seek(1024 * 1024 + 100) # 1MB + 100 bytes
        f.write(b"0")
    content = read_file(path=str(large_file))
    assert "exceeds" in content
    
    # 4. Binary file check
    binary_file = files_dir / "binary.bin"
    with open(binary_file, "wb") as f:
        f.write(b"Hello\0world")
    content = read_file(path=str(binary_file))
    assert "Binary" in content or "not allowed" in content

def test_describe_photo():
    # Insert mock photo
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO files (id, path, name, parent, is_file, size, modified, created)
        VALUES (200, '/tmp/lore_test_data/files/sunset_beach.jpg', 'sunset_beach.jpg', '/tmp/lore_test_data/files', 1, 500, 12345.0, 12345.0)
    """)
    cursor.execute("""
        INSERT INTO photos (file_id, path, name, mime, is_video, width, height, camera_model, capture_time, latitude, longitude)
        VALUES (200, '/tmp/lore_test_data/files/sunset_beach.jpg', 'sunset_beach.jpg', 'image/jpeg', 0, 1920, 1080, 'Canon EOS', 1700000000.0, 34.05, -118.24)
    """)
    conn.commit()
    conn.close()
    
    # Call describe_photo
    desc = describe_photo("200")
    assert "sunset" in desc or "beach" in desc
    assert "Canon EOS" in desc
    assert "1920x1080" in desc
    
    # Call describe_photo again (should return cached description)
    desc2 = describe_photo("200")
    assert desc2 == desc

def test_vector_search(monkeypatch):
    # Mock embed_text to return a dummy vector instead of running OpenCLIP
    monkeypatch.setattr("odin.tools.vector_search.embed_text", lambda query: [0.1] * 768)
    
    from psyche.init import get_collection
    collection = get_collection()
    dummy_vector = [0.1] * 768
    collection.add(
        embeddings=[dummy_vector],
        ids=["300"],
        metadatas=[{"path": "/tmp/lore_test_data/files/nature.jpg", "name": "nature.jpg"}]
    )
    
    res = vector_search("nature photo", top_k=1)
    results = json.loads(res)
    assert len(results) == 1
    assert results[0]["id"] == "300"
    assert results[0]["metadata"]["name"] == "nature.jpg"

def test_synchronized_deletions():
    # Insert mock file in database
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO files (id, path, name, parent, is_file, size, modified, created)
        VALUES (400, '/tmp/lore_test_data/files/delete_me.txt', 'delete_me.txt', '/tmp/lore_test_data/files', 1, 10, 12345.0, 12345.0)
    """)
    conn.commit()
    conn.close()
    
    # Create actual file on disk to prevent delete endpoint from raising 404
    file_path = Path("/tmp/lore_test_data/files/delete_me.txt")
    file_path.write_text("delete me")
    
    from psyche.init import get_collection
    collection = get_collection()
    dummy_vector = [0.1] * 768
    collection.add(
        embeddings=[dummy_vector],
        ids=["400"],
        metadatas=[{"path": str(file_path), "name": "delete_me.txt"}]
    )
    
    # Verify embedding is present
    res = collection.get(ids=["400"])
    assert len(res["ids"]) == 1
    
    # Call permanent delete via mimir router
    from fastapi.testclient import TestClient
    from bifrost.main import app
    client = TestClient(app)
    
    # Call with correct static key to authenticate
    headers = {"X-Api-Key": "test-secret-key"}
    del_res = client.delete("/mimir/delete?path=delete_me.txt&permanent=true", headers=headers)
    assert del_res.status_code == 200
    
    # Verify embedding is gone from ChromaDB!
    res_after = collection.get(ids=["400"])
    assert len(res_after["ids"]) == 0

