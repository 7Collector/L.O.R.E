import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

def read_webpage(url, timeout=6):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }

    try:
        response = requests.get(url, headers=headers, timeout=timeout)
        response.raise_for_status()
    except Exception as e:
        return {"error": str(e)}

    soup = BeautifulSoup(response.text, "lxml")

    # title
    title = soup.title.string.strip() if soup.title else ""

    # remove junk
    for tag in soup(["script", "style", "noscript", "iframe"]):
        tag.decompose()

    # extract text
    text = " ".join(soup.stripped_strings)

    # extract links
    links = []
    for a in soup.find_all("a", href=True):
        link = urljoin(url, a["href"])
        label = a.get_text(strip=True)
        if label or link:
            links.append({"text": label, "url": link})

    return {
        "url": response.url,
        "title": title,
        "content": text[:5000],
        "links": links[:50]
    }
