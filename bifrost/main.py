from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from mimir.orion.router import router as orion_router
from odin.router import router as odin_router
from mimir.router import router as mimir_router
#from bifrost.scheduler import scheduler
from odin.start_llm_server import start_llama_server
from bifrost.localtunnel import start_local_tunnel
from dotenv import load_dotenv
import os

load_dotenv()
API_KEY = os.getenv("API_KEY")
# Start the Fast API Server
app = FastAPI()

# For Testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Only allow our personal app
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    user_api_key = request.headers.get("X-Api-Key")
    if user_api_key != API_KEY:         
        return Response("Unauthorized", status_code=401)

    return await call_next(request)


# Start the Llama Server
server = start_llama_server()

# Start Local tunnel
tunnel = start_local_tunnel()

app.include_router(orion_router, prefix="/orion")
app.include_router(odin_router, prefix="/odin")
app.include_router(mimir_router, prefix="/mimir")

@app.get("/")
def check():
    return {"status": "ok"}

#@app.on_event("startup")
#async def start_scheduler():
    #if not scheduler.running:
        #scheduler.start()