from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.jobstores.sqlalchemy import SQLAlchemyJobStore
try:
    from mimir.orion.vision.embeddings import *
except ImportError:
    from ..mimir.orion.vision.embeddings import *

jobstores = {
    'default': SQLAlchemyJobStore(url='sqlite:///schedules.db')
}

scheduler = AsyncIOScheduler(jobstores=jobstores)