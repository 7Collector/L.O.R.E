from fastapi import FastAPI
from mimir.orion.router import router as orion_router
from odin.router import router as odin_router
from mimir.router import router as mimir_router


app = FastAPI()

app.include_router(orion_router, prefix="/orion")
app.include_router(odin_router, prefix="/odin")
app.include_router(mimir_router, prefix="/mimir")