import subprocess
import os

LT_PATH = r"C:\Users\raksh\AppData\Roaming\npm\node_modules\localtunnel\bin\lt"

PORT = "8080"
SUBDOMAIN = "rakshitscodeserver"

def start_code_server_tunnel():
    cmd = [
        "node",
        LT_PATH,
        "--port", PORT,
        "--subdomain", SUBDOMAIN
    ]

    print("Starting tunnel")

    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    return process

if(__name__ == "__main__"):
    start_code_server_tunnel()