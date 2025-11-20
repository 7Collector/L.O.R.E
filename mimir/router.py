import os
from fastapi import APIRouter, File, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse

from constants import BASE_DIR

router = APIRouter()

@router.get("download/{name}")
def fetch_file(name: str):
    path = os.path.join(BASE_DIR, name)

    if not os.path.exists(path):
        raise HTTPException(404, "File not found")

    if os.path.isdir(path):
        raise HTTPException(400, "Cannot fetch a folder")

    return FileResponse(path, filename=name)

@router.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    save_path = os.path.join(BASE_DIR, file.filename)

    if os.path.exists(save_path):
         raise HTTPException(400, "File already exists")

    with open(save_path, "wb") as out_file:
        content = await file.read()
        out_file.write(content)

    return {"status": "ok", "saved_as": file.filename}

@router.get("/list")
def get_list(
    path: str = Query("/", description="Folder to list"),
    page: int = 1,
    limit: int = 20
):
    folder = BASE_DIR + path

    if not folder.exists() or not folder.is_dir():
        raise HTTPException(404, "Folder not found")

    entries = list(folder.iterdir())
    entries.sort(key=lambda x: x.name.lower())

    start = (page - 1) * limit
    end = start + limit

    sliced = entries[start:end]

    result = []
    for e in sliced:
        stat = e.stat()
        result.append({
            "name": e.name,
            "is_file": e.is_file(),
            "is_dir": e.is_dir(),
            "size": stat.st_size,
            "modified": stat.st_mtime,
        })

    return {
        "page": page,
        "limit": limit,
        "total": len(entries),
        "data": result
    }

router.post("/create_folder")
def create_folder(req: Request, path: str = "/", name: str = ""):
    path = BASE_DIR + path
    try: 
        os.mkdir(path + name)
    except FileExistsError:
        return
    return

router.get("/delete")
def delete(req: Request, path: str = "/"):
    path = BASE_DIR + path
    try: 
        os.remove(path)
    except FileNotFoundError:
        return
    return

router.get("/rename")
def rename(req: Request, path: str = "/", name: str = ""):
    path = BASE_DIR + path
    try: 
        os.rename(path, path + name)
    except FileNotFoundError:
        return
    return