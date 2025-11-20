import sqlite3

conn = sqlite3.connect("saraswati.db")  # if file doesn't exist → it creates it
cursor = conn.cursor()

cursor.execute("""
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filename TEXT,
    path TEXT,
    ocr_text TEXT,
    created_at REAL
)
""")

conn.commit()
conn.close()
