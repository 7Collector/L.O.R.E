import subprocess
import time
from urllib.request import urlopen

# Absolute Paths
LLAMA_SERVER_EXE = r"L:\odin\llama.cpp\build\bin\Release\llama-server.exe"
MODEL_GGUF = r"L:\odin\llama.cpp\downloaded_models\gemma-3-12b-it-Q4_K_M.gguf"

# Arguments
CTX_SIZE      = "8192"
GPU_LAYERS    = "49"
THREADS       = "16"
PORT          = "8000"
FLASH_ATTN    = "on"
CHAT_TEMPLATE = "llama3"

server_url = f"http://127.0.0.1:{PORT}/health"

def start_llama_server():

    try:
        with urlopen(server_url) as response:
            if response.status == 200:
                print("\nServer is already running!")
    except Exception:
            print(".", end="", flush=True)

    cmd = [
        LLAMA_SERVER_EXE,
        "-m", MODEL_GGUF,
        "--ctx-size", CTX_SIZE,
        "--n-gpu-layers", GPU_LAYERS,
        "--threads", THREADS,
        "--port", PORT,
        "--flash-attn", FLASH_ATTN,
    ]

    print("\nStarting llama-server")
    print("Executable:", LLAMA_SERVER_EXE)
    print("Model:", MODEL_GGUF)
    print("Port:", PORT)

    process = subprocess.Popen(cmd)
    
    max_retries = 60
    for _ in range(max_retries):
        if process.poll() is not None:
            print("\nError: Server process terminated unexpectedly.")
            return None
            
        try:
            with urlopen(server_url) as response:
                if response.status == 200:
                    print("\nServer is ready!")
                    return process
        except Exception:
            time.sleep(1)
            print(".", end="", flush=True)
            
    print("\nTimeout: Server failed to start within time limit.")
    process.terminate()
    return None

if(__name__ == "__main__"):
    start_llama_server()