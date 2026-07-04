import time

from fastapi import Request
from fastapi.responses import JSONResponse

from app.config import settings
from app.db.client import verify_pb_token

PUBLIC_PATHS = {
    "/health",
    "/api/auth/login",
    "/api/auth/register",
    "/api/push/notify-release",
}
PUBLIC_PREFIXES = {"/docs", "/redoc", "/openapi.json"}

_token_cache: dict[str, tuple[float, dict]] = {}
CACHE_TTL = 300


async def auth_middleware(request: Request, call_next):
    path = request.url.path

    if path in PUBLIC_PATHS or any(path.startswith(p) for p in PUBLIC_PREFIXES):
        return await call_next(request)

    auth = request.headers.get("Authorization", "")
    if not auth:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    now = time.time()
    cached = _token_cache.get(auth)
    if cached and now - cached[0] < CACHE_TTL:
        request.state.user = cached[1]
        return await call_next(request)

    try:
        user = await verify_pb_token(auth)
    except Exception:
        return JSONResponse(status_code=401, content={"detail": "Token inválido o expirado"})

    _token_cache[auth] = (now, user)
    request.state.user = user

    return await call_next(request)
