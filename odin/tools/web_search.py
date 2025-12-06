import os
import requests
from dotenv import load_dotenv

load_dotenv()
GOOGLE_SEARCH_API_KEY = os.getenv("GOOGLE_SEARCH_API_KEY")
GOOGLE_SEARCH_CX = os.getenv("GOOGLE_SEARCH_CX")

def web_search(query, n):
    url = "https://www.googleapis.com/customsearch/v1"

    params = {
        "key": GOOGLE_SEARCH_API_KEY,
        "cx": GOOGLE_SEARCH_CX,
        "q": query,
        "num": n
    }

    try:
        response = requests.get(url, params=params, timeout=5)
        response.raise_for_status()

        data = response.json()
        items = data.get("items", [])

        results = []
        for item in items:
            results.append({
                "title": item.get("title"),
                "snippet": item.get("snippet"),
                "link": item.get("link")
            })

        return {"results": results}

    except requests.exceptions.RequestException as e:
        return {"error": str(e)}    