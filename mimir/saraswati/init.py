from pathlib import Path
import sqlite3

DB_PATH = Path("L:/saraswati.db")

DB_PATH.parent.mkdir(parents=True, exist_ok=True)

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()

cursor.executescript("""
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL,
    name TEXT NOT NULL,
    parent TEXT NOT NULL,
    is_file BOOLEAN NOT NULL,
    size INTEGER DEFAULT 0,
    modified REAL,
    created REAL,
    UNIQUE(path)
);

CREATE TABLE IF NOT EXISTS photos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    mime TEXT,
    is_video BOOLEAN,
    width INTEGER,
    height INTEGER,
    duration REAL,
    size INTEGER,
    created REAL,
    modified REAL,
    favorite BOOLEAN DEFAULT 0
);

CREATE TABLE IF NOT EXISTS albums ( fkjdsklfjkd
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    created REAL
);

CREATE TABLE IF NOT EXISTS album_items (
    album_id INTEGER NOT NULL,
    media_id INTEGER NOT NULL,
    PRIMARY KEY (album_id, media_id)
);
""")

conn.commit()
conn.close()
