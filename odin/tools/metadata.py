import sqlite3
import json
from constants import DB_PATH

def get_metadata(file_id: str):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    try:
        # Resolve file by id, path, or name in the files table
        file_id_str = str(file_id)
        if file_id_str.isdigit():
            cursor.execute("""
                SELECT id, path, name, parent, is_file, size, modified, created, favorite, owner_user_id, sha256, trashed, trashed_at
                FROM files
                WHERE id = ?
            """, (int(file_id_str),))
        else:
            cursor.execute("""
                SELECT id, path, name, parent, is_file, size, modified, created, favorite, owner_user_id, sha256, trashed, trashed_at
                FROM files
                WHERE id = ? OR path = ? OR name = ?
            """, (file_id_str, file_id_str, file_id_str))
            
        file_row = cursor.fetchone()
        if not file_row:
            return json.dumps({"error": f"File with ID/reference {file_id} not found in database"})
            
        # Build base metadata dict
        metadata = {
            "id": file_row["id"],
            "path": file_row["path"],
            "name": file_row["name"],
            "parent": file_row["parent"],
            "is_file": bool(file_row["is_file"]),
            "size": file_row["size"],
            "modified": file_row["modified"],
            "created": file_row["created"],
            "favorite": bool(file_row["favorite"]),
            "owner_user_id": file_row["owner_user_id"],
            "sha256": file_row["sha256"],
            "trashed": bool(file_row["trashed"]),
            "trashed_at": file_row["trashed_at"]
        }
        
        # Check if the file has additional metadata in the photos table
        cursor.execute("""
            SELECT mime, is_video, width, height, duration, capture_time, latitude, longitude, camera_model, orientation, description
            FROM photos
            WHERE file_id = ?
        """, (file_row["id"],))
        
        photo_row = cursor.fetchone()
        if photo_row:
            metadata["photo_metadata"] = {
                "mime": photo_row["mime"],
                "is_video": bool(photo_row["is_video"]),
                "width": photo_row["width"],
                "height": photo_row["height"],
                "duration": photo_row["duration"],
                "capture_time": photo_row["capture_time"],
                "latitude": photo_row["latitude"],
                "longitude": photo_row["longitude"],
                "camera_model": photo_row["camera_model"],
                "orientation": photo_row["orientation"],
                "description": photo_row["description"]
            }
            
        return json.dumps(metadata)
    except sqlite3.Error as e:
        return json.dumps({"error": f"Database error: {str(e)}"})
    finally:
        conn.close()
