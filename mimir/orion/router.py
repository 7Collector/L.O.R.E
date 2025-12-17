import os
import time
import sqlite3
import mimetypes
from pathlib import Path
from uuid import uuid4
from fastapi import APIRouter, UploadFile, File, HTTPException, Query, BackgroundTasks
from fastapi.responses import FileResponse
from PIL import Image

from constants import BASE_DIR, DB_PATH

router = APIRouter()
FILES_DIR = BASE_DIR / "files"
THUMB_DIR = FILES_DIR / ".thumbs"
FILES_DIR.mkdir(parents=True, exist_ok=True)
THUMB_DIR.mkdir(parents=True, exist_ok=True)

def db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def generate_thumb(src: Path, dest: Path):
    try:
        img = Image.open(src)
        img.thumbnail((300, 300))
        img.save(dest, "JPEG")
    except Exception as e:
        print(f"Error generating thumbnail: {e}")

@router.post("/upload")
async def upload_media(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    album_id: int | None = Query(None),
):
    content = await file.read()
    
    ext = Path(file.filename).suffix
    safe_filename = f"{uuid4().hex}{ext}"
    save_path = FILES_DIR / safe_filename
    thumb_path = THUMB_DIR / (safe_filename + ".jpg")

    save_path.write_bytes(content)

    mime, _ = mimetypes.guess_type(file.filename)
    if not mime:
        mime = "application/octet-stream"
    
    is_video = mime.startswith("video")
    width = height = duration = None
    size = len(content)
    now = time.time()

    if not is_video:
        try:
            img = Image.open(save_path)
            width, height = img.size
            background_tasks.add_task(generate_thumb, save_path, thumb_path)
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
        file_id = cur.lastrowid

        cur.execute(
            """
            INSERT INTO photos (
                file_id, path, name, mime, is_video,
                width, height, duration, size, created, modified
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                file_id, str(save_path), file.filename, mime,
                1 if is_video else 0, width, height, duration, 
                size, now, now
            ),
        )

        if album_id:
            cur.execute(
                "INSERT OR IGNORE INTO album_items (album_id, media_id) VALUES (?, ?)",
                (album_id, file_id),
            )

        conn.commit()
    finally:
        conn.close()

    return {"id": file_id, "name": file.filename}

@router.get("/file/{media_id}")
def get_file(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM files WHERE id = ?", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row or not Path(row[0]).exists():
        raise HTTPException(404, "Media not found")

    return FileResponse(row[0])

@router.get("/thumb/{media_id}")
def get_thumb(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM photos WHERE file_id = ?", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    original_path = Path(row[0])
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
               created, modified, favorite
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
    }

@router.get("/list")
def list_media(page: int = 1, limit: int = 50):
    conn = db()
    cur = conn.cursor()
    
    # Get total count first
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
    conn.execute("INSERT INTO albums (name, created) VALUES (?, ?)", (name, time.time()))
    conn.commit()
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
    conn = db()
    cur = conn.cursor()
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
    
    conn.commit()
    conn.close()
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