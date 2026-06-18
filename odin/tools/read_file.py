import os
import sqlite3
import json
import mimetypes
from pathlib import Path
from constants import BASE_DIR, DB_PATH

def read_file(file_id: str = None, path: str = None):
    try:
        resolved_path = None
        
        # 1. Resolve path from file_id or path parameter
        if file_id is not None:
            # Check if file_id is numeric (database ID)
            file_id_str = str(file_id)
            conn = sqlite3.connect(DB_PATH)
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            
            if file_id_str.isdigit():
                cursor.execute("SELECT path FROM files WHERE id = ?", (int(file_id_str),))
            else:
                cursor.execute("SELECT path FROM files WHERE id = ? OR path = ? OR name = ?", (file_id_str, file_id_str, file_id_str))
            
            row = cursor.fetchone()
            conn.close()
            
            if row:
                resolved_path = Path(row["path"])
            else:
                # If file_id didn't match a DB record, treat it as a potential relative/absolute path
                resolved_path = Path(file_id_str)
        elif path is not None:
            resolved_path = Path(path)
        else:
            return "Error: Neither file_id nor path was provided"

        # 2. Canonicalize path
        storage_root = Path(BASE_DIR / "files").resolve()
        abs_path = resolved_path.resolve()

        # 3. Prevent path traversal (Assert inside storage root)
        try:
            abs_path.relative_to(storage_root)
        except ValueError:
            return "Error: Access Denied: Path is outside the authorized storage root"

        # 4. Check existence
        if not abs_path.exists():
            return f"Error: File not found: {abs_path.name}"
        if not abs_path.is_file():
            return "Error: Path is a directory, not a file"

        # 5. Restrict file size (max 1MB)
        if abs_path.stat().st_size > 1024 * 1024:
            return "Error: Access Denied: File size exceeds the 1MB limit"

        # 6. Reject binary/media files
        mime_type, _ = mimetypes.guess_type(str(abs_path))
        is_text = False
        if mime_type:
            if mime_type.startswith("text/") or mime_type in ["application/json", "application/xml", "application/javascript", "image/svg+xml"]:
                is_text = True
        else:
            # Fallback to reading first block
            is_text = True

        if is_text:
            try:
                with open(abs_path, "rb") as f:
                    chunk = f.read(1024)
                    if b"\0" in chunk:
                        is_text = False
            except Exception:
                is_text = False

        if not is_text:
            return "Error: Access Denied: Binary or media files are not allowed"

        # 7. Read and return text content
        with open(abs_path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()

    except Exception as e:
        return f"Error: {str(e)}"
