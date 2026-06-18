import sqlite3
import time
from constants import DB_PATH

def describe_photo(photo_id: str):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    try:
        # Resolve photo by file_id, name, or path
        photo_id_str = str(photo_id)
        if photo_id_str.isdigit():
            cursor.execute("""
                SELECT file_id, path, name, mime, width, height, camera_model, capture_time, latitude, longitude, description 
                FROM photos 
                WHERE file_id = ?
            """, (int(photo_id_str),))
        else:
            cursor.execute("""
                SELECT file_id, path, name, mime, width, height, camera_model, capture_time, latitude, longitude, description 
                FROM photos 
                WHERE file_id = ? OR name = ? OR path = ?
            """, (photo_id_str, photo_id_str, photo_id_str))
            
        row = cursor.fetchone()
        
        if not row:
            return f"Error: Photo with ID {photo_id} not found in photos database"
        
        file_id = row["file_id"]
        cached_description = row["description"]
        
        if cached_description:
            return cached_description
        
        # Generate description using metadata
        parts = []
        name = row["name"]
        width = row["width"]
        height = row["height"]
        camera = row["camera_model"]
        capture = row["capture_time"]
        lat = row["latitude"]
        lon = row["longitude"]
        mime = row["mime"]
        
        # Check filename clues
        clues = []
        clean_name = name.lower().replace("_", " ").replace("-", " ")
        for word in ["sunset", "sunrise", "beach", "cat", "dog", "mountain", "lake", "forest", "party", "food", "car", "wedding", "birthday", "concert"]:
            if word in clean_name:
                clues.append(word)
        
        subject = "A photo"
        if clues:
            subject = f"A photo featuring {', '.join(clues)}"
        
        parts.append(f"{subject} (named '{name}').")
        
        if width and height:
            parts.append(f"Resolution: {width}x{height} pixels.")
        if mime:
            parts.append(f"Format: {mime}.")
        if camera:
            parts.append(f"Captured using {camera}.")
        if capture:
            try:
                # Convert timestamp to human readable date
                readable_date = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(capture))
                parts.append(f"Taken on {readable_date}.")
            except Exception:
                pass
        if lat is not None and lon is not None:
            parts.append(f"Location coordinates: {lat}, {lon}.")
            
        description = " ".join(parts)
        
        # Cache the generated description in SQLite
        cursor.execute("UPDATE photos SET description = ? WHERE file_id = ?", (description, file_id))
        conn.commit()
        
        return description
    except sqlite3.Error as e:
        return f"Error describing photo: {str(e)}"
    finally:
        conn.close()
