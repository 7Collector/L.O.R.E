import sqlite3
import json
from constants import DB_PATH

def file_search(query: str, limit: int = 10):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    try:
        # First, try using FTS5 MATCH
        # Escape or format query for MATCH syntax safely
        fts_query = f"{query}*"
        
        cursor.execute("""
            SELECT file_id, name, path FROM files_fts 
            WHERE files_fts MATCH ? 
            LIMIT ?
        """, (fts_query, limit))
        
        rows = cursor.fetchall()
        
        # If no results, try fallback with LIKE
        if not rows:
            cursor.execute("""
                SELECT id as file_id, name, path FROM files 
                WHERE name LIKE ? OR path LIKE ? 
                LIMIT ?
            """, (f"%{query}%", f"%{query}%", limit))
            rows = cursor.fetchall()
            
        results = []
        for r in rows:
            file_id = r["file_id"]
            # Fetch additional metadata from files table
            cursor.execute("""
                SELECT is_file, size, modified, created, favorite, trashed 
                FROM files WHERE id = ?
            """, (file_id,))
            file_info = cursor.fetchone()
            
            item = {
                "file_id": file_id,
                "name": r["name"],
                "path": r["path"],
            }
            if file_info:
                item.update({
                    "is_file": bool(file_info["is_file"]),
                    "size": file_info["size"],
                    "modified": file_info["modified"],
                    "created": file_info["created"],
                    "favorite": bool(file_info["favorite"]),
                    "trashed": bool(file_info["trashed"])
                })
                
            # Check if this is a photo
            cursor.execute("""
                SELECT mime, width, height, is_video FROM photos WHERE file_id = ?
            """, (file_id,))
            photo_info = cursor.fetchone()
            if photo_info:
                item["photo_metadata"] = {
                    "mime": photo_info["mime"],
                    "width": photo_info["width"],
                    "height": photo_info["height"],
                    "is_video": bool(photo_info["is_video"])
                }
            results.append(item)
            
        return json.dumps(results)
        
    except sqlite3.Error as e:
        # Fallback to pure LIKE search on SQLite error
        try:
            cursor.execute("""
                SELECT id as file_id, name, path FROM files 
                WHERE name LIKE ? OR path LIKE ? 
                LIMIT ?
            """, (f"%{query}%", f"%{query}%", limit))
            rows = cursor.fetchall()
            results = []
            for r in rows:
                file_id = r["file_id"]
                cursor.execute("""
                    SELECT is_file, size, modified, created, favorite, trashed 
                    FROM files WHERE id = ?
                """, (file_id,))
                file_info = cursor.fetchone()
                item = {
                    "file_id": file_id,
                    "name": r["name"],
                    "path": r["path"],
                }
                if file_info:
                    item.update({
                        "is_file": bool(file_info["is_file"]),
                        "size": file_info["size"],
                        "modified": file_info["modified"],
                        "created": file_info["created"],
                        "favorite": bool(file_info["favorite"]),
                        "trashed": bool(file_info["trashed"])
                    })
                cursor.execute("""
                    SELECT mime, width, height, is_video FROM photos WHERE file_id = ?
                """, (file_id,))
                photo_info = cursor.fetchone()
                if photo_info:
                    item["photo_metadata"] = {
                        "mime": photo_info["mime"],
                        "width": photo_info["width"],
                        "height": photo_info["height"],
                        "is_video": bool(photo_info["is_video"])
                    }
                results.append(item)
            return json.dumps(results)
        except Exception as fallback_err:
            return json.dumps({"error": str(fallback_err)})
    finally:
        conn.close()
