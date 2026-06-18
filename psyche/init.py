import chromadb
try:
    from constants import *
except ImportError:
    from ..constants import *

client = chromadb.PersistentClient(path=PSYCHE_PATH)

def get_collection():
    return client.get_or_create_collection(
        name="file_embeddings",
        metadata={"hnsw:space": "cosine"}
    )

collection = get_collection()