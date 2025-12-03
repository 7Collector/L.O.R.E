import chromadb
from ..constants import *

client = chromadb.PersistentClient(path=PSYCHE_PATH)

collection = client.get_or_create_collection(
    name="file_embeddings",
    metadata={"hnsw:space": "cosine"}
)