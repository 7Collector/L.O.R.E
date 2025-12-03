import os
import time
import sqlite3
from pathlib import Path
from fastapi import APIRouter, UploadFile, File, HTTPException, Query
from fastapi.responses import FileResponse
from PIL import Image
import mimetypes

from constants import BASE_DIR, DB_PATH

router = APIRouter()
BASE_DIR = Path(BASE_DIR / "files")
THUMB_DIR = BASE_DIR / ".thumbs"
THUMB_DIR.mkdir(parents=True, exist_ok=True)


def db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def generate_thumb(src: Path, dest: Path):
    img = Image.open(src)
    img.thumbnail((300, 300))
    img.save(dest, "JPEG")


# Upload Media File
@router.post("/upload")
async def upload_media(
    file: UploadFile = File(...),
    album_id: int | None = Query(None),
):
    content = await file.read()

    mime, _ = mimetypes.guess_type(file.filename)
    is_video = mime and mime.startswith("video")

    save_path = BASE_DIR / file.filename
    thumb_path = THUMB_DIR / (file.filename + ".jpg")

    if save_path.exists():
        raise HTTPException(400, "File already exists")

    save_path.write_bytes(content)

    width = height = duration = None
    size = len(content)
    now = time.time()

    if not is_video:
        img = Image.open(save_path)
        width, height = img.size
        generate_thumb(save_path, thumb_path)

    conn = db()
    cur = conn.cursor()

    cur.execute(
        """
        INSERT INTO files (path, name, parent, is_file, size, modified, created)
        VALUES (?, ?, ?, 1, ?, ?, ?)
        """,
        (
            str(save_path),
            file.filename,
            str(save_path.parent),
            size,
            now,
            now,
        ),
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
            file_id,
            str(save_path),
            file.filename,
            mime,
            1 if is_video else 0,
            width,
            height,
            duration,
            size,
            now,
            now,
        ),
    )

    if album:
        cur.execute(
            """
            INSERT OR IGNORE INTO album_items (album_id, media_id)
            """,
            (album_id, file_id),
        )

    conn.commit()
    conn.close()

    return {"id": file_id, "name": file.filename}


# Download Media File
@router.get("/file/{media_id}")
def get_file(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM files WHERE id = ?", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    return FileResponse(row[0])


# Download Thumbnail File
@router.get("/thumb/{media_id}")
def get_thumb(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM photos WHERE file_id = ?", (media_id,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise HTTPException(404, "Media not found")

    thumb_path = THUMB_DIR / (Path(row[0]).name + ".jpg")
    if not thumb_path.exists():
        raise HTTPException(404, "No thumbnail")

    return FileResponse(thumb_path)


# Get Metadata
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


# Get Media List
@router.get("/list")
def list_media(page: int = 1, limit: int = 50):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT file_id, name, mime, favorite FROM photos ORDER BY created DESC")
    rows = cur.fetchall()
    conn.close()

    start = (page - 1) * limit
    sliced = rows[start:start + limit]

    return {
        "page": page,
        "limit": limit,
        "total": len(rows),
        "data": [
            {
                "id": r[0],
                "name": r[1],
                "mime": r[2],
                "favorite": bool(r[3]),
            }
            for r in sliced
        ],
    }


# Create Album
@router.post("/album")
def create_album(name: str):
    conn = db()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO albums (name, created) VALUES (?, ?)",
        (name, time.time()),
    )
    conn.commit()
    conn.close()
    return {"status": "ok", "album": name}


# Add Media to Album
@router.post("/album/{album_id}/add")
def add_to_album(album_id: int, media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute(
        "INSERT OR IGNORE INTO album_items (album_id, media_id) VALUES (?, ?)",
        (album_id, media_id),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}


# List all Albums
@router.get("/albums")
def list_albums():
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT id, name FROM albums ORDER BY name ASC")
    rows = cur.fetchall()
    conn.close()
    return [{"id": r[0], "name": r[1]} for r in rows]


# List Album Items, NEEDS PAGINATION
@router.get("/album/{album_id}")
def album_items(album_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT photos.file_id, photos.name, photos.mime
        FROM album_items
        JOIN photos ON photos.file_id = album_items.media_id
        WHERE album_items.album_id = ?
        ORDER BY photos.created DESC
        """,
        (album_id,),
    )
    rows = cur.fetchall()
    conn.close()

    return [
        {
            "id": r[0],
            "name": r[1],
            "mime": r[2],
        }
        for r in rows
    ]


# Delete Media File
@router.delete("/delete/{media_id}")
def delete_media(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT path FROM files WHERE id = ?", (media_id,))
    row = cur.fetchone()

    if not row:
        conn.close()
        raise HTTPException(404, "Media not found")

    path = Path(row[0])
    if path.exists():
        path.unlink()

    thumb = THUMB_DIR / (path.name + ".jpg")
    if thumb.exists():
        thumb.unlink()

    cur.execute("DELETE FROM album_items WHERE media_id = ?", (media_id,))

    cur.execute("DELETE FROM photos WHERE file_id = ?", (media_id,))

    cur.execute("DELETE FROM files WHERE id = ?", (media_id,))

    conn.commit()
    conn.close()

    return {"status": "ok"}


# Favourite Media
@router.post("/favorite/{media_id}")
def favorite(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("UPDATE photos SET favorite = 1 WHERE file_id = ?", (media_id,))
    conn.commit()
    conn.close()
    return {"favorite": True}


# Unfavourite Media
@router.post("/unfavorite/{media_id}")
def unfavorite(media_id: int):
    conn = db()
    cur = conn.cursor()
    cur.execute("UPDATE photos SET favorite = 0 WHERE file_id = ?", (media_id,))
    conn.commit()
    conn.close()
    return {"favorite": False}
