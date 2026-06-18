import os
from pathlib import Path
from dotenv import load_dotenv

# Load configuration from .env if present
load_dotenv(Path(__file__).parent / ".env")

# Resolve directories relative to project root by default
DEFAULT_BASE = Path(__file__).parent / "data"

BASE_DIR = Path(os.getenv("LORE_BASE_DIR", str(DEFAULT_BASE)))
DB_PATH = Path(os.getenv("LORE_DB_PATH", str(BASE_DIR / "saraswati_db")))
PSYCHE_PATH = Path(os.getenv("LORE_PSYCHE_PATH", str(BASE_DIR / "psyche_db")))