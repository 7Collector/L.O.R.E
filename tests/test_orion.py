import os
import time
import sqlite3
import shutil
import subprocess
import datetime
import pytest
from io import BytesIO
from pathlib import Path
from fastapi.testclient import TestClient
from PIL import Image

# Configure testing environment variables before importing main
os.environ["API_KEY"] = "test-secret-key"
os.environ["LORE_BASE_DIR"] = "/tmp/lore_test_data"
os.environ["LORE_DB_PATH"] = "/tmp/lore_test_data/saraswati_db"
os.environ["LORE_PSYCHE_PATH"] = "/tmp/lore_test_data/psyche_db"
os.environ["SMTP_HOST"] = "mock"

from constants import DB_PATH
from bifrost.main import app
from mimir.saraswati.init import run_migrations

def get_headers():
    return {"X-Api-Key": "test-secret-key"}

@pytest.fixture(autouse=True)
def setup_test_db():
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

    # Reset ChromaDB collection
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

def test_upload_photo_metadata():
    client = TestClient(app)
    headers = get_headers()

    # Create image with EXIF metadata
    im = Image.new("RGB", (100, 100), color="red")
    exif = im.getexif()
    exif[272] = "MyCameraModel"  # Model
    exif[274] = 3                # Orientation (180 deg)
    exif[36867] = "2026:06:18 12:00:00"  # DateTimeOriginal
    
    # GPSInfo (latitude 40.446, longitude -79.982)
    # Ref 'N' / 'W'
    exif[34853] = {
        1: 'N',
        2: (40.0, 26.0, 45.6),
        3: 'W',
        4: (79.0, 58.0, 55.2)
    }

    buf = BytesIO()
    im.save(buf, format="JPEG", exif=exif)
    img_bytes = buf.getvalue()

    # Upload photo
    files = {"file": ("test_photo.jpg", img_bytes, "image/jpeg")}
    res = client.post("/orion/upload?file_id=img1.jpg", files=files, headers=headers)
    assert res.status_code == 200
    media_id = res.json()["id"]
    assert media_id

    # Wait for jobs (thumbnail, embedding) to complete
    for _ in range(20):
        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        cur.execute("SELECT status FROM jobs")
        statuses = [r[0] for r in cur.fetchall()]
        conn.close()
        if len(statuses) >= 2 and all(s in ('done', 'failed') for s in statuses):
            break
        time.sleep(0.5)

    # Get info
    info_res = client.get(f"/orion/info/{media_id}", headers=headers)
    assert info_res.status_code == 200
    data = info_res.json()
    assert data["camera_model"] == "MyCameraModel"
    assert data["orientation"] == 3
    assert data["latitude"] is not None
    assert abs(data["latitude"] - 40.446) < 0.01
    assert data["longitude"] is not None
    assert abs(data["longitude"] - (-79.982)) < 0.01

    # Check thumbnail download
    thumb_res = client.get(f"/orion/thumb/{media_id}", headers=headers)
    assert thumb_res.status_code == 200

def test_upload_video_metadata_and_thumb():
    # Generate a tiny 1-second video with ffmpeg
    video_path = "/tmp/test_vid.mp4"
    if os.path.exists(video_path):
        os.remove(video_path)

    subprocess.run([
        'ffmpeg', '-y', '-f', 'lavfi', '-i', 'color=c=blue:s=320x240:d=1',
        '-metadata', 'creation_time=2026-06-18T12:00:00.000000Z',
        '-c:v', 'libx264', '-t', '1', video_path
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    assert os.path.exists(video_path)

    client = TestClient(app)
    headers = get_headers()

    with open(video_path, "rb") as f:
        video_bytes = f.read()

    files = {"file": ("test_vid.mp4", video_bytes, "video/mp4")}
    res = client.post("/orion/upload?file_id=vid1.mp4", files=files, headers=headers)
    assert res.status_code == 200
    media_id = res.json()["id"]

    # Wait for jobs to complete
    for _ in range(20):
        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        cur.execute("SELECT status FROM jobs")
        statuses = [r[0] for r in cur.fetchall()]
        conn.close()
        if len(statuses) >= 2 and all(s in ('done', 'failed') for s in statuses):
            break
        time.sleep(0.5)

    # Clean up test temp file
    if os.path.exists(video_path):
        os.remove(video_path)

    # Get video info
    info_res = client.get(f"/orion/info/{media_id}", headers=headers)
    assert info_res.status_code == 200
    data = info_res.json()
    assert data["is_video"] is True
    assert data["width"] == 320
    assert data["height"] == 240
    assert data["duration"] is not None

    # Check thumbnail exists and returns 200
    thumb_res = client.get(f"/orion/thumb/{media_id}", headers=headers)
    assert thumb_res.status_code == 200

def test_duplicate_check_options():
    client = TestClient(app)
    headers = get_headers()

    # Save initial image
    im = Image.new("RGB", (100, 100), color="blue")
    buf = BytesIO()
    im.save(buf, format="JPEG")
    img_bytes = buf.getvalue()

    # Upload A
    files = {"file": ("test_photo.jpg", img_bytes, "image/jpeg")}
    res = client.post("/orion/upload?file_id=imgA.jpg", files=files, headers=headers)
    assert res.status_code == 200
    id_a = res.json()["id"]

    # Wait for embedding generation job to complete
    for _ in range(20):
        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        cur.execute("SELECT status FROM jobs WHERE task_name = 'generate_embedding'")
        status = cur.fetchone()
        conn.close()
        if status and status[0] in ('done', 'failed'):
            break
        time.sleep(0.5)

    # 1. Upload duplicate without duplicate_action -> Expect 409 Conflict
    res2 = client.post("/orion/upload?file_id=imgDup.jpg", files=files, headers=headers)
    assert res2.status_code == 409
    assert res2.json()["status"] == "duplicate"
    assert res2.json()["duplicate_id"] == id_a

    # 2. Upload duplicate with duplicate_action=skip
    res3 = client.post("/orion/upload?file_id=imgDup.jpg&duplicate_action=skip", files=files, headers=headers)
    assert res3.status_code == 200
    assert res3.json()["status"] == "skipped"
    assert res3.json()["id"] == id_a

    # 3. Upload duplicate with duplicate_action=keep-both
    res4 = client.post("/orion/upload?file_id=imgDup.jpg&duplicate_action=keep-both", files=files, headers=headers)
    assert res4.status_code == 200
    id_dup = res4.json()["id"]
    assert id_dup != id_a

    # 4. Upload duplicate with duplicate_action=replace
    res5 = client.post("/orion/upload?file_id=imgReplace.jpg&duplicate_action=replace", files=files, headers=headers)
    assert res5.status_code == 200
    id_replace = res5.json()["id"]
    
    # Verify old file was deleted
    info_res = client.get(f"/orion/info/{id_a}", headers=headers)
    assert info_res.status_code == 404

def test_timeline():
    client = TestClient(app)
    headers = get_headers()

    # Photo A (older)
    im = Image.new("RGB", (10, 10), color="yellow")
    exif = im.getexif()
    exif[36867] = "2024:06:18 12:00:00"
    buf = BytesIO()
    im.save(buf, format="JPEG", exif=exif)
    res_a = client.post("/orion/upload?file_id=older.jpg", files={"file": ("older.jpg", buf.getvalue(), "image/jpeg")}, headers=headers)

    # Photo B (newer)
    im_b = Image.new("RGB", (10, 10), color="magenta")
    exif_b = im_b.getexif()
    exif_b[36867] = "2026:06:18 12:00:00"
    buf_b = BytesIO()
    im_b.save(buf_b, format="JPEG", exif=exif_b)
    res_b = client.post("/orion/upload?file_id=newer.jpg", files={"file": ("newer.jpg", buf_b.getvalue(), "image/jpeg")}, headers=headers)

    # Call timeline
    timeline_res = client.get("/orion/timeline", headers=headers)
    assert timeline_res.status_code == 200
    items = timeline_res.json()["data"]
    
    # Newest should be first
    assert items[0]["id"] == res_b.json()["id"]
    assert items[1]["id"] == res_a.json()["id"]

def test_memories():
    client = TestClient(app)
    headers = get_headers()

    now = datetime.datetime.now()
    capture_year = now.year - 2
    capture_dt_str = f"{capture_year:04d}:{now.month:02d}:{now.day:02d} 12:00:00"

    im = Image.new("RGB", (10, 10), color="cyan")
    exif = im.getexif()
    exif[36867] = capture_dt_str
    buf = BytesIO()
    im.save(buf, format="JPEG", exif=exif)

    res = client.post("/orion/upload?file_id=memory.jpg", files={"file": ("memory.jpg", buf.getvalue(), "image/jpeg")}, headers=headers)
    media_id = res.json()["id"]

    # Call memories
    mem_res = client.get("/orion/memories", headers=headers)
    assert mem_res.status_code == 200
    memories = mem_res.json()
    
    found = False
    for memory in memories:
        if memory["year"] == capture_year:
            assert memory["title"] == "2 years ago"
            assert memory["photos"][0]["id"] == media_id
            found = True
            break
    assert found

def test_map_bounding_box():
    client = TestClient(app)
    headers = get_headers()

    # Photo A (inside bounding box)
    im = Image.new("RGB", (10, 10), color="orange")
    exif = im.getexif()
    exif[34853] = {
        1: 'N',
        2: (40.0, 0.0, 0.0),
        3: 'W',
        4: (80.0, 0.0, 0.0)
    }
    buf = BytesIO()
    im.save(buf, format="JPEG", exif=exif)
    res_a = client.post("/orion/upload?file_id=map_in.jpg", files={"file": ("map_in.jpg", buf.getvalue(), "image/jpeg")}, headers=headers)
    id_a = res_a.json()["id"]

    # Photo B (outside bounding box)
    im_b = Image.new("RGB", (10, 10), color="purple")
    exif_b = im_b.getexif()
    exif_b[34853] = {
        1: 'N',
        2: (50.0, 0.0, 0.0),
        3: 'W',
        4: (120.0, 0.0, 0.0)
    }
    buf_b = BytesIO()
    im_b.save(buf_b, format="JPEG", exif=exif_b)
    res_b = client.post("/orion/upload?file_id=map_out.jpg", files={"file": ("map_out.jpg", buf_b.getvalue(), "image/jpeg")}, headers=headers)
    id_b = res_b.json()["id"]

    # Map request bounding box: lat 38 to 42, lon -85 to -75
    map_res = client.get("/orion/map?min_lat=38.0&max_lat=42.0&min_lon=-85.0&max_lon=-75.0", headers=headers)
    assert map_res.status_code == 200
    points = map_res.json()
    
    ids = [p["id"] for p in points]
    assert id_a in ids
    assert id_b not in ids

def test_bulk_operations():
    client = TestClient(app)
    headers = get_headers()

    # Image 1 (red)
    im1 = Image.new("RGB", (10, 10), color="red")
    buf1 = BytesIO()
    im1.save(buf1, format="JPEG")
    img_bytes1 = buf1.getvalue()

    # Image 2 (green)
    im2 = Image.new("RGB", (10, 10), color="green")
    buf2 = BytesIO()
    im2.save(buf2, format="JPEG")
    img_bytes2 = buf2.getvalue()

    # Upload two photos
    res1 = client.post("/orion/upload?file_id=bulk1.jpg", files={"file": ("bulk1.jpg", img_bytes1, "image/jpeg")}, headers=headers)
    res2 = client.post("/orion/upload?file_id=bulk2.jpg", files={"file": ("bulk2.jpg", img_bytes2, "image/jpeg")}, headers=headers)
    id1 = res1.json()["id"]
    id2 = res2.json()["id"]

    # Bulk favorite
    bulk_fav_res = client.post("/orion/bulk", json={
        "action": "favorite",
        "media_ids": [id1, id2]
    }, headers=headers)
    assert bulk_fav_res.status_code == 200

    # Verify favorited
    info1 = client.get(f"/orion/info/{id1}", headers=headers).json()
    info2 = client.get(f"/orion/info/{id2}", headers=headers).json()
    assert info1["favorite"] is True
    assert info2["favorite"] is True

    # Bulk unfavorite
    bulk_unfav_res = client.post("/orion/bulk", json={
        "action": "unfavorite",
        "media_ids": [id1, id2]
    }, headers=headers)
    assert bulk_unfav_res.status_code == 200
    info1 = client.get(f"/orion/info/{id1}", headers=headers).json()
    assert info1["favorite"] is False

    # Bulk delete
    bulk_del_res = client.post("/orion/bulk", json={
        "action": "delete",
        "media_ids": [id1, id2]
    }, headers=headers)
    assert bulk_del_res.status_code == 200

    # Verify deleted
    assert client.get(f"/orion/info/{id1}", headers=headers).status_code == 404
    assert client.get(f"/orion/info/{id2}", headers=headers).status_code == 404

def test_path_traversal_safety():
    client = TestClient(app)
    headers = get_headers()

    # Try upload with double dot
    im = Image.new("RGB", (10, 10), color="grey")
    buf = BytesIO()
    im.save(buf, format="JPEG")
    res = client.post("/orion/upload?file_id=../../bad.jpg", files={"file": ("bad.jpg", buf.getvalue(), "image/jpeg")}, headers=headers)
    assert res.status_code == 400
