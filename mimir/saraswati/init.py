import sqlite3
import time
from pathlib import Path
from constants import DB_PATH

def run_migrations():
    print(f"Running database migrations/initialization on: {DB_PATH}")
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Enable foreign keys
    cursor.execute("PRAGMA foreign_keys = ON")

    # 1. Create the user and authentication/sharing tables
    cursor.executescript("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT NOT NULL UNIQUE,
        password_hash TEXT,
        display_name TEXT,
        is_owner BOOLEAN DEFAULT 0,
        created REAL
    );

    CREATE TABLE IF NOT EXISTS sessions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        token_hash TEXT NOT NULL UNIQUE,
        device_label TEXT,
        created REAL,
        expires_at REAL,
        revoked BOOLEAN DEFAULT 0,
        FOREIGN KEY (user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS shares (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        resource_type TEXT NOT NULL,        -- 'file' | 'folder' | 'album'
        resource_id INTEGER NOT NULL,
        owner_user_id INTEGER NOT NULL,
        shared_with_user_id INTEGER,        -- NULL for link-only shares
        permission TEXT NOT NULL,           -- 'view' | 'comment' | 'edit'
        created REAL,
        expires_at REAL,
        revoked BOOLEAN DEFAULT 0,
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
        FOREIGN KEY (shared_with_user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS share_links (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        share_id INTEGER NOT NULL,
        token_hash TEXT NOT NULL UNIQUE,
        requires_email BOOLEAN DEFAULT 0,
        allowed_emails TEXT,                -- JSON array, nullable
        password_hash TEXT,
        max_uses INTEGER,
        use_count INTEGER DEFAULT 0,
        created REAL,
        expires_at REAL,
        revoked BOOLEAN DEFAULT 0,
        FOREIGN KEY (share_id) REFERENCES shares(id)
    );

    CREATE TABLE IF NOT EXISTS audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp REAL NOT NULL,
        user_id INTEGER,
        action TEXT NOT NULL,
        target_resource TEXT,
        ip_address TEXT,
        status TEXT NOT NULL
    );
    """)

    # 2. Bootstrap Owner User
    cursor.execute("SELECT id FROM users WHERE is_owner = 1 LIMIT 1")
    owner = cursor.fetchone()
    if not owner:
        cursor.execute(
            "INSERT INTO users (email, display_name, is_owner, created) VALUES (?, ?, ?, ?)",
            ("owner@lore.local", "Owner", 1, time.time())
        )
        owner_id = cursor.lastrowid
        print(f"Created default owner user with ID: {owner_id}")
    else:
        owner_id = owner[0]

    # 3. Create core system tables if they don't exist (with owner_user_id default)
    cursor.executescript(f"""
    CREATE TABLE IF NOT EXISTS files (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        path TEXT NOT NULL,
        name TEXT NOT NULL,
        parent TEXT NOT NULL,
        is_file BOOLEAN NOT NULL,
        size INTEGER DEFAULT 0,
        modified REAL,
        created REAL,
        favorite BOOLEAN DEFAULT 0,
        owner_user_id INTEGER DEFAULT {owner_id},
        sha256 TEXT,
        trashed BOOLEAN DEFAULT 0,
        trashed_at REAL DEFAULT NULL,
        UNIQUE(path),
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS photos (
        file_id INTEGER PRIMARY KEY,
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
        favorite BOOLEAN DEFAULT 0,
        owner_user_id INTEGER DEFAULT {owner_id},
        description TEXT,
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS albums (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,
        created REAL,
        owner_user_id INTEGER DEFAULT {owner_id},
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS album_items (
        album_id INTEGER NOT NULL,
        media_id INTEGER NOT NULL,
        PRIMARY KEY (album_id, media_id)
    );

    CREATE TABLE IF NOT EXISTS jobs (
        job_id INTEGER PRIMARY KEY AUTOINCREMENT,
        task_name TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('pending', 'running', 'done', 'failed')),
        retries INTEGER DEFAULT 0,
        payload TEXT,
        created_at REAL,
        updated_at REAL
    );
    """)

    # 4. Migrate existing tables to add owner_user_id if missing (idempotent ALTER TABLE)
    for table in ["files", "photos", "albums"]:
        cursor.execute(f"PRAGMA table_info({table})")
        columns = [col[1] for col in cursor.fetchall()]
        if "owner_user_id" not in columns:
            print(f"Altering table {table} to add column owner_user_id...")
            cursor.execute(f"ALTER TABLE {table} ADD COLUMN owner_user_id INTEGER DEFAULT {owner_id}")

    # Add other columns to files if missing
    cursor.execute("PRAGMA table_info(files)")
    files_columns = [col[1] for col in cursor.fetchall()]
    if "sha256" not in files_columns:
        print("Altering table files to add column sha256...")
        cursor.execute("ALTER TABLE files ADD COLUMN sha256 TEXT")
    if "trashed" not in files_columns:
        print("Altering table files to add column trashed...")
        cursor.execute("ALTER TABLE files ADD COLUMN trashed BOOLEAN DEFAULT 0")
    if "trashed_at" not in files_columns:
        print("Altering table files to add column trashed_at...")
        cursor.execute("ALTER TABLE files ADD COLUMN trashed_at REAL DEFAULT NULL")

    # Add columns to photos table if missing
    cursor.execute("PRAGMA table_info(photos)")
    photos_columns = [col[1] for col in cursor.fetchall()]
    if "capture_time" not in photos_columns:
        print("Altering table photos to add column capture_time...")
        cursor.execute("ALTER TABLE photos ADD COLUMN capture_time REAL")
    if "latitude" not in photos_columns:
        print("Altering table photos to add column latitude...")
        cursor.execute("ALTER TABLE photos ADD COLUMN latitude REAL")
    if "longitude" not in photos_columns:
        print("Altering table photos to add column longitude...")
        cursor.execute("ALTER TABLE photos ADD COLUMN longitude REAL")
    if "camera_model" not in photos_columns:
        print("Altering table photos to add column camera_model...")
        cursor.execute("ALTER TABLE photos ADD COLUMN camera_model TEXT")
    if "orientation" not in photos_columns:
        print("Altering table photos to add column orientation...")
        cursor.execute("ALTER TABLE photos ADD COLUMN orientation INTEGER")
    if "description" not in photos_columns:
        print("Altering table photos to add column description...")
        cursor.execute("ALTER TABLE photos ADD COLUMN description TEXT")

    # 5. Create FTS5 virtual table and triggers
    cursor.executescript("""
    CREATE VIRTUAL TABLE IF NOT EXISTS files_fts USING fts5(
        file_id UNINDEXED,
        name,
        path
    );

    CREATE TRIGGER IF NOT EXISTS files_ai AFTER INSERT ON files BEGIN
        INSERT INTO files_fts(file_id, name, path) VALUES (new.id, new.name, new.path);
    END;

    CREATE TRIGGER IF NOT EXISTS files_ad AFTER DELETE ON files BEGIN
        DELETE FROM files_fts WHERE file_id = old.id;
    END;

    CREATE TRIGGER IF NOT EXISTS files_au AFTER UPDATE ON files BEGIN
        DELETE FROM files_fts WHERE file_id = old.id;
        INSERT INTO files_fts(file_id, name, path) VALUES (new.id, new.name, new.path);
    END;
    """)

    # Populate FTS5 table with existing files
    cursor.execute("""
    INSERT OR IGNORE INTO files_fts(file_id, name, path)
    SELECT id, name, path FROM files
    WHERE id NOT IN (SELECT file_id FROM files_fts)
    """)

    conn.commit()
    conn.close()
    print("Database migrations and schema initialization completed successfully.")

if __name__ == "__main__":
    run_migrations()
