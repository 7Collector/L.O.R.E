import chromadb

client = chromadb.PersistentClient(path="psyche_db")

collection = client.get_or_create_collection(
    name="file_embeddings",
    metadata={"hnsw:space": "cosine"}  # cosine similarity for text
)
