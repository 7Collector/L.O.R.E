from fastapi import FastAPI
from bifrost.middleware import check_source
from mimir.orion import router as orion_router
from odin import router as odin_router
from mimir import router as mimir_router


app = FastAPI()

app.include_router(orion_router, prefix="/orion")
app.include_router(odin_router, prefix="/odin")
app.include_router(mimir_router, prefix="/mimir")

app.add_middleware(check_source)