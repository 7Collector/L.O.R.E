import os
import time
import sqlite3
from pathlib import Path
from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse

from constants import BASE_DIR, DB_PATH

router = APIRouter()
BASE_DIR = Path(BASE_DIR / "files")

def db():
    return sqlite3.connect(DB_PATH)


# Download a file
@router.get("/download/{path:path}")
def fetch_file(path: str):
    full_path = BASE_DIR / path

    if not full_path.exists() or full_path.is_dir():
        raise HTTPException(404, "File not found")

    return FileResponse(full_path, filename=full_path.name)


# Upload a file to the respective path
@router.post("/upload")
async def upload_file(
    path: str = Query("/", description="Folder to upload to"),
    file: UploadFile = File(...)
):
    folder = BASE_DIR / path.lstrip("/")
    folder.mkdir(parents=True, exist_ok=True)

    save_path = folder / file.filename

    if save_path.exists():
        raise HTTPException(400, "File already exists")

    content = await file.read()
    save_path.write_bytes(content)

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        INSERT OR IGNORE INTO files (path, name, parent, is_file, size, modified, created)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            str(save_path),
            file.filename,
            str(folder),
            1,
            len(content),
            time.time(),
            time.time(),
        ),
    )
    conn.commit()
    conn.close()

    return {"status": "ok", "saved_as": save_path.name}


# List files and Sub Dirs
@router.get("/list")
def get_list(
    path: str = Query("/", description="Folder to list"),
    page: int = 1,
    limit: int = 20,
):
    folder = BASE_DIR / path.lstrip("/")

    if not folder.exists() or not folder.is_dir():
        raise HTTPException(404, "Folder not found")

    conn = db()
    cur = conn.cursor()
    cur.execute(
        "SELECT name, is_file, size, modified FROM files WHERE parent = ? ORDER BY name ASC",
        (str(folder),),
    )
    rows = cur.fetchall()
    conn.close()

    start = (page - 1) * limit
    sliced = rows[start:start + limit]

    data = [
        {
            "name": r[0],
            "is_file": bool(r[1]),
            "size": r[2],
            "modified": r[3],
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

    parent = BASE_DIR / path.lstrip("/")
    new_folder = parent / name

    if new_folder.exists():
        raise HTTPException(400, "Folder already exists")

    new_folder.mkdir(parents=True, exist_ok=False)

    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        INSERT OR IGNORE INTO files (path, name, parent, is_file, size, modified, created)
        VALUES (?, ?, ?, 0, 0, ?, ?)
        """,
        (str(new_folder), name, str(parent), time.time(), time.time()),
    )
    conn.commit()
    conn.close()

    return {"status": "ok", "created": str(new_folder)}


# Delete a file or folder
@router.delete("/delete")
def delete(path: str = "/"):
    target = BASE_DIR / path.lstrip("/")

    if not target.exists():
        raise HTTPException(404, "Path not found")

    conn = db()
    cur = conn.cursor()

    if target.is_file():
        target.unlink()
        cur.execute("DELETE FROM files WHERE path = ?", (str(target),))
    else:
        for item in target.glob("**/*"):
            if item.is_file():
                item.unlink()
                cur.execute("DELETE FROM files WHERE path = ?", (str(item),))
        for item in sorted(target.glob("**/*"), reverse=True):
            if item.is_dir():
                item.rmdir()
                cur.execute("DELETE FROM files WHERE path = ?", (str(item),))
        target.rmdir()
        cur.execute("DELETE FROM files WHERE path = ?", (str(target),))

    conn.commit()
    conn.close()

    return {"status": "ok", "deleted": path}


# Rename a file or folder
@router.put("/rename")
def rename(path: str = "/", name: str = ""):
    if not name:
        raise HTTPException(400, "New name required")

    target = BASE_DIR / path.lstrip("/")

    if not target.exists():
        raise HTTPException(404, "Path not found")

    new_path = target.parent / name

    if new_path.exists():
        raise HTTPException(400, "Name already exists")

    target.rename(new_path)

    conn = db()
    cur = conn.cursor()

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
    conn.close()

    return {"status": "ok", "old": str(target), "new": str(new_path)}
