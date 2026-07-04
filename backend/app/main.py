import threading
from fastapi import FastAPI
from app.api.routes import router
from app.config import settings
from app.db.client import _get_client
from app.updater import check_and_apply_update

app = FastAPI(
    title="Hermetic AI",
    description="Motor de IA soberano personal",
    version="0.1.0",
)

app.include_router(router)

@app.on_event("startup")
def startup_event():
    # Launch updates check in background thread
    threading.Thread(target=check_and_apply_update, daemon=True).start()


def main():
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=True,
    )


if __name__ == "__main__":
    main()
