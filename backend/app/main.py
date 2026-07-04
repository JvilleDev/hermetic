import threading
from fastapi import FastAPI
from app.api.routes import router
from app.config import settings
from app.middleware.auth import auth_middleware
from app.updater import start_periodic_updates

app = FastAPI(
    title="Hermetic AI",
    description="Motor de IA soberano personal",
    version="0.1.3",
)

app.middleware("http")(auth_middleware)

app.include_router(router)

@app.on_event("startup")
def startup_event():
    threading.Thread(target=start_periodic_updates, daemon=True).start()


def main():
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=False,
    )


if __name__ == "__main__":
    main()
