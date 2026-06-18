import json
from psyche.init import get_collection
from mimir.orion.vision.embeddings import embed_text

def vector_search(query: str, top_k: int = 5):
    try:
        emb = embed_text(query)
        collection = get_collection()
        results = collection.query(
            query_embeddings=[emb],
            n_results=top_k
        )
        
        matches = []
        if results and "ids" in results and results["ids"]:
            ids = results["ids"][0]
            distances = results.get("distances", [[]])[0]
            metadatas = results.get("metadatas", [[]])[0]
            
            for i in range(len(ids)):
                match = {
                    "id": ids[i],
                    "metadata": metadatas[i] if i < len(metadatas) else {}
                }
                if i < len(distances):
                    # Cosine distance to similarity: similarity = 1 - distance
                    match["similarity"] = 1.0 - distances[i]
                matches.append(match)
        return json.dumps(matches)
    except Exception as e:
        return json.dumps({"error": str(e)})
