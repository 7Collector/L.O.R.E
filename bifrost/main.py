from fastapi import FastAPI
from mimir.orion.router import router as orion_router
from odin.router import router as odin_router
from mimir.router import router as mimir_router
from scheduler import scheduler
from odin.start_llm_server import start_llama_server

# Start the Fast API Server
app = FastAPI()

# Start the Llama Server
server = start_llama_server()

app.include_router(orion_router, prefix="/orion")
app.include_router(odin_router, prefix="/odin")
app.include_router(mimir_router, prefix="/mimir")

@app.on_event("startup")
async def start_scheduler():
    if not scheduler.running:
        scheduler.start()