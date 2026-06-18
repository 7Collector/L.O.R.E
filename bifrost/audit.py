import time
import sqlite3
from constants import DB_PATH

def log_audit(user_id: int | None, action: str, target_resource: str | None, ip_address: str | None, status: str):
    """
    Records an entry in the audit_logs table.
    """
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    try:
        cur.execute(
            """
            INSERT INTO audit_logs (timestamp, user_id, action, target_resource, ip_address, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (time.time(), user_id, action, target_resource, ip_address, status)
        )
        conn.commit()
    except Exception as e:
        # Avoid crashing the request on audit logging failure, but log it to stderr
        import sys
        print(f"Error writing to audit_logs: {e}", file=sys.stderr)
    finally:
        conn.close()
