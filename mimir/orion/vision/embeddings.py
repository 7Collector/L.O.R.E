import os
import torch
from PIL import Image
from dotenv import load_dotenv

load_dotenv()
LOCAL_LLM = os.getenv("LOCAL_LLM", "False").lower() in ("true", "1", "yes")

device = None
model = None
preprocess = None

if LOCAL_LLM:
    import open_clip
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model, preprocess, _ = open_clip.create_model_and_transforms("ViT-L-14", pretrained="openai")
    model = model.to(device)

def embed_image(path: str):
    if not LOCAL_LLM:
        # Return dummy unit vector if local AI workflows are disabled
        return [1.0] + [0.0] * 767
    img = Image.open(path).convert("RGB")
    tensor = preprocess(img).unsqueeze(0).to(device)
    with torch.no_grad():
        emb = model.encode_image(tensor)
    emb = emb / emb.norm(dim=-1, keepdim=True)
    return emb.cpu().numpy()[0].tolist()

def embed_video(path: str):
    return

def embed_text(text: str):
    if not LOCAL_LLM:
        # Return dummy unit vector if local AI workflows are disabled
        return [1.0] + [0.0] * 767
    import open_clip
    tokenizer = open_clip.get_tokenizer("ViT-L-14")
    tokenized_text = tokenizer([text]).to(device)
    with torch.no_grad():
        emb = model.encode_text(tokenized_text)
    emb = emb / emb.norm(dim=-1, keepdim=True)
    return emb.cpu().numpy()[0].tolist()