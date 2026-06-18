import os
import time
import sqlite3
import mimetypes
import json
import subprocess
import traceback
import threading
import datetime
import collections
import shutil
from pathlib import Path
from uuid import uuid4
from fastapi import APIRouter, UploadFile, File, HTTPException, Query
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel
from PIL import Image
from PIL.ExifTags import TAGS, GPSTAGS

from constants import BASE_DIR, DB_PATH

router = APIRouter()
FILES_DIR = BASE_DIR / "files"
THUMB_DIR = FILES_DIR / ".thumbs"
TEMP_UPLOAD_DIR = FILES_DIR / ".tmp_uploads"

FILES_DIR.mkdir(parents=True, exist_ok=True)
THUMB_DIR.mkdir(parents=True, exist_ok=True)
TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

def db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def safe_resolve(user_path: str | Path) -> Path:
    FILES_DIR.mkdir(parents=True, exist_ok=True)
    THUMB_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    if isinstance(user_path, Path):
        user_path_str = str(user_path)
    else:
        user_path_str = user_path

    # Check if user_path_str is already resolved under FILES_DIR
    try:
        p = Path(user_path_str).resolve()
        p.relative_to(FILES_DIR)
        return p
    except ValueError:
        pass

    # Treat as relative to FILES_DIR
    rel_path = user_path_str.lstrip("/")
    target = (FILES_DIR / rel_path).resolve()

    try:
        target.relative_to(FILES_DIR)
    except ValueError:
        raise HTTPException(400, "Directory traversal detected")

    return target

def generate_thumb(src: Path, dest: Path, is_video: bool = False):
    try:
        if is_video:
            # Try 1 second first, fallback to 0 seconds if error or empty
            cmd = [
                'ffmpeg', '-y', '-i', str(src),
                '-ss', '00:00:01', '-vframes', '1',
                '-f', 'image2', str(dest)
            ]
            subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if not dest.exists() or dest.stat().st_size == 0:
                cmd = [
                    'ffmpeg', '-y', '-i', str(src),
                    '-ss', '00:00:00', '-vframes', '1',
                    '-f', 'image2', str(dest)
                ]
                subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            # Post-process thumbnail with PIL to scale/compress (e.g. 300x300 max)
            if dest.exists() and dest.stat().st_size > 0:
                img = Image.open(dest)
                img.thumbnail((300, 300))
                img.save(dest, "JPEG")
        else:
            img = Image.open(src)
            img.thumbnail((300, 300))
            img.save(dest, "JPEG")
    except Exception as e:
        print(f"Error generating thumbnail: {e}")

def get_video_info(path: Path):
    cmd = [
        'ffprobe', '-v', 'quiet', '-print_format', 'json',
        '-show_streams', '-show_format', str(path)
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode == 0:
        data = json.loads(res.stdout)
        width = None
        height = None
        duration = None
        
        # Get video stream
        for stream in data.get('streams', []):
            if stream.get('codec_type') == 'video':
                width = int(stream.get('width')) if stream.get('width') is not None else None
                height = int(stream.get('height')) if stream.get('height') is not None else None
                break
        
        # Get duration from format or video stream
        duration_str = data.get('format', {}).get('duration')
        if not duration_str:
            for stream in data.get('streams', []):
                if stream.get('duration'):
                    duration_str = stream.get('duration')
                    break
        if duration_str:
            duration = float(duration_str)
            
        return width, height, duration
    return None, None, None

def extract_exif(path: Path):
    res = {
        "capture_time": None,
        "latitude": None,
        "longitude": None,
        "camera_model": None,
        "orientation": None
    }
    try:
        img = Image.open(path)
        exif = img._getexif()
        if not exif:
            return res
        
        # Get standard EXIF tags
        exif_data = {}
        for tag, val in exif.items():
            decoded = TAGS.get(tag, tag)
            exif_data[decoded] = val
        
        # 1. Orientation
        res["orientation"] = exif_data.get("Orientation")
        
        # 2. Camera Model
        res["camera_model"] = exif_data.get("Model")
        
        # 3. Capture Date/Time
        dt_str = exif_data.get("DateTimeOriginal") or exif_data.get("DateTimeDigitized") or exif_data.get("DateTime")
        if dt_str:
            try:
                cleaned_dt = dt_str.split("+")[0].split("-")[0].strip()
                t_struct = time.strptime(cleaned_dt, "%Y:%m:%d %H:%M:%S")
                res["capture_time"] = time.mktime(t_struct)
            except Exception:
                pass
        
        # 4. GPS Info
        gps_info = exif_data.get("GPSInfo")
        if gps_info:
            gps_data = {}
            for t in gps_info:
                sub_decoded = GPSTAGS.get(t, t)
                gps_data[sub_decoded] = gps_info[t]
            
            def to_float(val):
                if isinstance(val, (int, float)):
                    return float(val)
                try:
                    return float(val.numerator) / float(val.denominator)
                except AttributeError:
                    try:
                        return float(val[0]) / float(val[1])
                    except (IndexError, TypeError, ZeroDivisionError):
                        return float(val)

            def get_decimal_coord(ref, degrees, minutes, seconds):
                d = to_float(degrees)
                m = to_float(minutes)
                s = to_float(seconds)
                decimal = d + (m / 60.0) + (s / 3600.0)
                if ref in ['S', 'W']:
                    decimal = -decimal
                return decimal

            lat_ref = gps_data.get("GPSLatitudeRef")
            lat_val = gps_data.get("GPSLatitude")
            lon_ref = gps_data.get("GPSLongitudeRef")
            lon_val = gps_data.get("GPSLongitude")

            if lat_ref and lat_val and len(lat_val) == 3:
                res["latitude"] = get_decimal_coord(lat_ref, lat_val[0], lat_val[1], lat_val[2])
            if lon_ref and lon_val and len(lon_val) == 3:
                res["longitude"] = get_decimal_coord(lon_ref, lon_val[0], lon_val[1], lon_val[2])
    except Exception as e:
        print(f"Error parsing EXIF: {e}")
    return res

def extract_video_exif(path: Path):
    res = {
        "capture_time": None,
        "latitude": None,
        "longitude": None,
        "camera_model": None,
        "orientation": None
    }
    try:
        cmd = [
            'ffprobe', '-v', 'quiet', '-print_format', 'json',
            '-show_streams', '-show_format', str(path)
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode == 0:
            data = json.loads(proc.stdout)
            tags = data.get('format', {}).get('tags', {})
            creation_time = tags.get('creation_time')
            if not creation_time:
                for stream in data.get('streams', []):
                    creation_time = stream.get('tags', {}).get('creation_time')
                    if creation_time:
                        break
            if creation_time:
                for fmt in ("%Y-%m-%dT%H:%M:%S.%fZ", "%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%SZ", "%Y-%m-%dT%H:%M:%S"):
                    try:
                        cleaned = creation_time
                        if '.' in cleaned:
                            parts = cleaned.split('.')
                            cleaned = parts[0] + 'Z' if cleaned.endswith('Z') else parts[0]
                        t_struct = time.strptime(cleaned.replace('Z', ''), "%Y-%m-%dT%H:%M:%S")
                        res["capture_time"] = time.mktime(t_struct)
                        break
                    except Exception:
                        pass
            
            for stream in data.get('streams', []):
                if stream.get('codec_type') == 'video':
                    rot = stream.get('tags', {}).get('rotate')
                    if rot:
                        res["orientation"] = int(rot)
    except Exception as e:
        print(f"Error parsing video EXIF: {e}")
    return res

def delete_media_logic(media_id: int):
    conn = db()
    cur = conn.cursor()
    try:
        cur.execute("SELECT path FROM files WHERE id = ?", (media_id,))
        row = cur.fetchone()

        if row:
            path = Path(row[0])
            thumb = THUMB_DIR / (path.name + ".jpg")
            try:
                if path.exists(): path.unlink()
                if thumb.exists(): thumb.unlink()
            except OSError:
                pass

        cur.execute("DELETE FROM album_items WHERE media_id = ?", (media_id,))
        cur.execute("DELETE FROM photos WHERE file_id = ?", (media_id,))
        cur.execute("DELETE FROM files WHERE id = ?", (media_id,))
        
        # Also delete from chromadb
        try:
            from psyche.init import collection
            collection.delete(ids=[str(media_id)])
        except Exception as e:
            print(f"Error deleting from ChromaDB: {e}")
            
        conn.commit()
    finally:
        conn.close()

def execute_job(task_name: str, payload: dict):
    FILES_DIR.mkdir(parents=True, exist_ok=True)
    THUMB_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    if task_name == "generate_thumbnail":
        media_id = payload["media_id"]
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT path, is_video FROM photos WHERE file_id = ?", (media_id,))
        row = cur.fetchone()
        conn.close()
        if not row:
            raise Exception(f"Media {media_id} not found in photos")
        src_path = Path(row[0])
        thumb_path = THUMB_DIR / (src_path.name + ".jpg")
        generate_thumb(src_path, thumb_path, is_video=bool(row[1]))
    elif task_name == "generate_embedding":
        media_id = payload["media_id"]
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT path, is_video FROM photos WHERE file_id = ?", (media_id,))
        row = cur.fetchone()
        conn.close()
        if not row:
            raise Exception(f"Media {media_id} not found in photos")
        src_path = Path(row[0])
        is_video = bool(row[1])
        
        embed_target = src_path
        if is_video:
            thumb_path = THUMB_DIR / (src_path.name + ".jpg")
            if not thumb_path.exists():
                generate_thumb(src_path, thumb_path, is_video=True)
            if thumb_path.exists():
                embed_target = thumb_path
            else:
                raise Exception("Thumbnail not available for video embedding")
        
        from mimir.orion.vision.embeddings import embed_image
        emb = embed_image(str(embed_target))
        
        from psyche.init import get_collection
        collection = get_collection()
        try:
            collection.delete(ids=[str(media_id)])
        except Exception:
            pass
        collection.add(
            embeddings=[emb],
            ids=[str(media_id)],
            metadatas=[{"path": str(src_path), "name": src_path.name}]
        )

def run_job_worker():
    time.sleep(2)  # Give migrations time to run
    while True:
        conn = None
        try:
            conn = db()
            cur = conn.cursor()
            cur.execute(
                """
                SELECT job_id, task_name, payload, retries 
                FROM jobs 
                WHERE status = 'pending' 
                ORDER BY created_at ASC 
                LIMIT 1
                """
            )
            row = cur.fetchone()
            if not row:
                conn.close()
                conn = None
                time.sleep(1)
                continue
            
            job_id, task_name, payload_str, retries = row
            cur.execute(
                "UPDATE jobs SET status = 'running', updated_at = ? WHERE job_id = ?",
                (time.time(), job_id)
            )
            conn.commit()
            conn.close()
            conn = None
            
            try:
                payload = json.loads(payload_str) if payload_str else {}
                execute_job(task_name, payload)
                
                conn = db()
                cur = conn.cursor()
                cur.execute(
                    "UPDATE jobs SET status = 'done', updated_at = ? WHERE job_id = ?",
                    (time.time(), job_id)
                )
                conn.commit()
                conn.close()
                conn = None
            except Exception as e:
                print(f"Job {job_id} failed: {e}")
                traceback.print_exc()
                
                conn = db()
                cur = conn.cursor()
                if retries < 3:
                    cur.execute(
                        "UPDATE jobs SET status = 'pending', retries = retries + 1, updated_at = ? WHERE job_id = ?",
                        (time.time(), job_id)
                    )
                else:
                    cur.execute(
                        "UPDATE jobs SET status = 'failed', updated_at = ? WHERE job_id = ?",
                        (time.time(), job_id)
                    )
                conn.commit()
                conn.close()
                conn = None
        except Exception as e:
            if conn:
                try:
                    conn.close()
                except Exception:
                    pass
                conn = None
            time.sleep(2)
            print(f"Error in job worker loop: {e}")

@router.post("/upload")
async def upload_media(
    file: UploadFile = File(...),
    file_id: str = Query(...),
    album_id: int | None = Query(None),
    duplicate_action: str | None = Query(None),
):
    FILES_DIR.mkdir(parents=True, exist_ok=True)
    THUMB_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

    # Validate file_id directory traversal
    try:
        temp_path = (TEMP_UPLOAD_DIR / file_id).resolve()
        temp_path.relative_to(FILES_DIR)
    except ValueError:
        raise HTTPException(400, "Directory traversal detected")

    content = await file.read()
    temp_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path.write_bytes(content)

    mime, _ = mimetypes.guess_type(file.filename)
    if not mime:
        mime = "application/octet-stream"
    
    is_video = mime.startswith("video")
    
    # 1. Duplicate check
    existing_id = None
    if not is_video:
        try:
            from mimir.orion.vision.embeddings import embed_image
            new_emb = embed_image(str(temp_path))
            
            from psyche.init import get_collection
            collection = get_collection()
            results = collection.query(
                query_embeddings=[new_emb],
                n_results=1
            )
            if results and results["distances"] and len(results["distances"][0]) > 0:
                dist = results["distances"][0][0]
                if dist <= 0.05:
                    existing_id = int(results["ids"][0][0])
        except Exception as e:
            print(f"Error doing duplicate check: {e}")

    if existing_id is not None:
        if duplicate_action is None:
            temp_path.unlink(missing_ok=True)
            return JSONResponse(
                status_code=409,
                content={
                    "status": "duplicate",
                    "message": "Duplicate photo detected",
                    "duplicate_id": existing_id
                }
            )
        elif duplicate_action == "skip":
            temp_path.unlink(missing_ok=True)
            return {"status": "skipped", "id": existing_id}
        elif duplicate_action == "replace":
            delete_media_logic(existing_id)
        # keep-both flows through normally

    # Finalize save
    save_path = safe_resolve(file_id)
    shutil.move(str(temp_path), str(save_path))

    width = height = duration = None
    size = len(content)
    now = time.time()
    
    capture_time = latitude = longitude = camera_model = orientation = None

    if not is_video:
        try:
            img = Image.open(save_path)
            width, height = img.size
            
            # Extract EXIF
            exif_res = extract_exif(save_path)
            capture_time = exif_res["capture_time"]
            latitude = exif_res["latitude"]
            longitude = exif_res["longitude"]
            camera_model = exif_res["camera_model"]
            orientation = exif_res["orientation"]
        except Exception:
            pass
    else:
        try:
            w, h, dur = get_video_info(save_path)
            width = w
            height = h
            duration = dur
            
            # Extract video metadata
            video_exif = extract_video_exif(save_path)
            capture_time = video_exif["capture_time"]
            latitude = video_exif["latitude"]
            longitude = video_exif["longitude"]
            camera_model = video_exif["camera_model"]
            orientation = video_exif["orientation"]
        except Exception:
            pass

    conn = db()
    cur = conn.cursor()
    
    try:
        cur.execute(
            """
            INSERT INTO files (path, name, parent, is_file, size, modified, created)
            VALUES (?, ?, ?, 1, ?, ?, ?)
            """,
            (str(save_path), file.filename, str(FILES_DIR), size, now, now),
        )
        inserted_id = cur.lastrowid

        cur.execute(
            """
            INSERT INTO photos (
                file_id, path, name, mime, is_video,
                width, height, duration, size, created, modified,
                capture_time, latitude, longitude, camera_model, orientation
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                inserted_id, str(save_path), file.filename, mime,
                1 if is_video else 0, width, height, duration, 
                size, now, now,
                capture_time, latitude, longitude, camera_model, orientation
            ),
        )

        if album_id:
            cur.execute(
                "INSERT OR IGNORE INTO album_items (album_id, media_id) VALUES (?, ?)",
                (album_id, inserted_id),
            )

        # Queue jobs
        cur.execute(
            """
            INSERT INTO jobs (task_name, status, retries, payload, created_at, updated_at)
            VALUES (?, 'pending', 0, ?, ?, ?)
            """,
            ("generate_thumbnail", json.dumps({"media_id": inserted_id}), now, now)
        )
        cur.execute(
            """
            INSERT INTO jobs (task_name, status, retries, payload, created_at, updated_at)
            VALUES (?, 'pending', 0, ?, ?, ?)
            """,
            ("generate_embedding", json.dumps({"media_id": inserted_id}), now, now)
        )

        conn.commit()
    finally:
        conn.close()

    return {"id": inserted_id, "name": file.filename}

@router.get("/file/{media_id}")
def get_file(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM files WHERE id = ? AND (trashed = 0 OR trashed IS NULL)", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    resolved_path = safe_resolve(row[0])
    if not resolved_path.exists():
        raise HTTPException(404, "Media file does not exist")

    return FileResponse(resolved_path)

@router.get("/thumb/{media_id}")
def get_thumb(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM photos WHERE file_id = ?", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    original_path = safe_resolve(row[0])
    thumb_path = THUMB_DIR / (original_path.name + ".jpg")
    
    if not thumb_path.exists():
        if original_path.exists():
             return FileResponse(original_path)
        raise HTTPException(404, "No thumbnail")

    return FileResponse(thumb_path)

@router.get("/info/{media_id}")
def get_info(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT file_id, path, name, mime, is_video,
               width, height, duration, size,
               created, modified, favorite,
               capture_time, latitude, longitude, camera_model, orientation
        FROM photos WHERE file_id = ?
        """,
        (media_id,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    return {
        "id": row[0],
        "path": row[1],
        "name": row[2],
        "mime": row[3],
        "is_video": bool(row[4]),
        "width": row[5],
        "height": row[6],
        "duration": row[7],
        "size": row[8],
        "created": row[9],
        "modified": row[10],
        "favorite": bool(row[11]),
        "capture_time": row[12],
        "latitude": row[13],
        "longitude": row[14],
        "camera_model": row[15],
        "orientation": row[16],
    }

@router.get("/list")
def list_media(page: int = 1, limit: int = 50):
    conn = db()
    cur = conn.cursor()
    
    cur.execute("SELECT COUNT(*) FROM photos")
    total = cur.fetchone()[0]

    cur.execute(
        """
        SELECT file_id, name, mime, favorite 
        FROM photos 
        ORDER BY created DESC 
        LIMIT ? OFFSET ?
        """,
        (limit, (page - 1) * limit)
    )
    rows = cur.fetchall()
    conn.close()

    return {
        "page": page,
        "limit": limit,
        "total": total,
        "data": [
            {"id": r[0], "name": r[1], "mime": r[2], "favorite": bool(r[3])}
            for r in rows
        ],
    }

@router.post("/album")
def create_album(name: str):
    conn = db()
    try:
        conn.execute("INSERT INTO albums (name, created) VALUES (?, ?)", (name, time.time()))
        conn.commit()
    except sqlite3.IntegrityError:
        pass
    finally:
        conn.close()
    return {"status": "ok", "album": name}

@router.post("/album/{album_id}/add")
def add_to_album(album_id: int, media_id: int):
    conn = db()
    conn.execute(
        "INSERT OR IGNORE INTO album_items (album_id, media_id) VALUES (?, ?)",
        (album_id, media_id),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}

@router.get("/albums")
def list_albums():
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT id, name FROM albums ORDER BY name ASC")
    rows = cur.fetchall()
    conn.close()
    return [{"id": r[0], "name": r[1]} for r in rows]

@router.get("/album/{album_id}")
def album_items(album_id: int, page: int = 1, limit: int = 50):
    conn = db()
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*) FROM album_items WHERE album_id = ?", (album_id,))
    total = cur.fetchone()[0]

    cur.execute(
        """
        SELECT photos.file_id, photos.name, photos.mime, photos.favorite
        FROM album_items
        JOIN photos ON photos.file_id = album_items.media_id
        WHERE album_items.album_id = ?
        ORDER BY photos.created DESC
        LIMIT ? OFFSET ?
        """,
        (album_id, limit, (page - 1) * limit),
    )
    rows = cur.fetchall()
    conn.close()

    return {
        "page": page,
        "limit": limit,
        "total": total,
        "data": [
            {"id": r[0], "name": r[1], "mime": r[2], "favorite": bool(r[3])}
            for r in rows
        ],
    }

@router.delete("/delete/{media_id}")
def delete_media(media_id: int):
    delete_media_logic(media_id)
    return {"status": "ok"}

@router.post("/favorite/{media_id}")
def favorite(media_id: int):
    conn = db()
    conn.execute("UPDATE photos SET favorite = 1 WHERE file_id = ?", (media_id,))
    conn.commit()
    conn.close()
    return {"favorite": True}

@router.post("/unfavorite/{media_id}")
def unfavorite(media_id: int):
    conn = db()
    conn.execute("UPDATE photos SET favorite = 0 WHERE file_id = ?", (media_id,))
    conn.commit()
    conn.close()
    return {"favorite": False}

@router.get("/timeline")
def get_timeline(page: int = 1, limit: int = 50):
    conn = db()
    cur = conn.cursor()
    
    cur.execute("SELECT COUNT(*) FROM photos")
    total = cur.fetchone()[0]

    cur.execute(
        """
        SELECT file_id, name, mime, favorite, COALESCE(capture_time, modified) as date_taken
        FROM photos 
        ORDER BY date_taken DESC 
        LIMIT ? OFFSET ?
        """,
        (limit, (page - 1) * limit)
    )
    rows = cur.fetchall()
    conn.close()

    return {
        "page": page,
        "limit": limit,
        "total": total,
        "data": [
            {"id": r[0], "name": r[1], "mime": r[2], "favorite": bool(r[3]), "date_taken": r[4]}
            for r in rows
        ],
    }

@router.get("/memories")
def get_memories():
    now = datetime.datetime.now()
    month_day = f"{now.month:02d}-{now.day:02d}"
    current_year = now.year

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT file_id, name, mime, favorite, COALESCE(capture_time, modified) as date_taken
        FROM photos
        WHERE strftime('%m-%d', datetime(COALESCE(capture_time, modified), 'unixepoch')) = ?
          AND strftime('%Y', datetime(COALESCE(capture_time, modified), 'unixepoch')) < ?
        ORDER BY date_taken DESC
        """,
        (month_day, str(current_year))
    )
    rows = cur.fetchall()
    conn.close()

    memories_by_year = collections.defaultdict(list)
    for file_id, name, mime, favorite, date_taken in rows:
        dt = datetime.datetime.fromtimestamp(date_taken)
        year = dt.year
        years_ago = current_year - year
        if years_ago > 0:
            memories_by_year[year].append({
                "id": file_id,
                "name": name,
                "mime": mime,
                "favorite": bool(favorite),
                "date": dt.isoformat()
            })

    result = []
    for year in sorted(memories_by_year.keys(), reverse=True):
        years_ago = current_year - year
        title = f"{years_ago} year{'s' if years_ago > 1 else ''} ago"
        result.append({
            "year": year,
            "title": title,
            "photos": memories_by_year[year]
        })
    return result

@router.get("/map")
def get_map(
    min_lat: float = Query(...),
    max_lat: float = Query(...),
    min_lon: float = Query(...),
    max_lon: float = Query(...)
):
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT file_id, name, mime, favorite, latitude, longitude
        FROM photos
        WHERE latitude BETWEEN ? AND ?
          AND longitude BETWEEN ? AND ?
        """,
        (min_lat, max_lat, min_lon, max_lon)
    )
    rows = cur.fetchall()
    conn.close()

    return [
        {
            "id": r[0],
            "name": r[1],
            "mime": r[2],
            "favorite": bool(r[3]),
            "latitude": r[4],
            "longitude": r[5],
            "thumbnail_url": f"/orion/thumb/{r[0]}"
        }
        for r in rows
    ]

class BulkOperation(BaseModel):
    action: str
    media_ids: list[int]
    album_id: int | None = None

@router.post("/bulk")
def bulk_operation(op: BulkOperation):
    if op.action == "delete":
        for media_id in op.media_ids:
            delete_media_logic(media_id)
    elif op.action == "favorite":
        conn = db()
        conn.executemany("UPDATE photos SET favorite = 1 WHERE file_id = ?", [(mid,) for mid in op.media_ids])
        conn.commit()
        conn.close()
    elif op.action == "unfavorite":
        conn = db()
        conn.executemany("UPDATE photos SET favorite = 0 WHERE file_id = ?", [(mid,) for mid in op.media_ids])
        conn.commit()
        conn.close()
    elif op.action == "add_to_album":
        if op.album_id is None:
            raise HTTPException(400, "album_id is required for add_to_album action")
        conn = db()
        conn.executemany(
            "INSERT OR IGNORE INTO album_items (album_id, media_id) VALUES (?, ?)",
            [(op.album_id, mid) for mid in op.media_ids]
        )
        conn.commit()
        conn.close()
    else:
        raise HTTPException(400, f"Unsupported action: {op.action}")

    return {"status": "ok"}

# Start the background worker thread
worker_thread = threading.Thread(target=run_job_worker, daemon=True)
worker_thread.start()