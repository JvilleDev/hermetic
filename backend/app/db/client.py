import logging

from httpx import AsyncClient
from fastapi import HTTPException
from app.config import settings

logger = logging.getLogger("db")

_client: AsyncClient | None = None


async def _get_client() -> AsyncClient:
    global _client
    if _client is None:
        _client = AsyncClient(base_url=settings.pocketbase_url)
        r = await _client.post(
            "/api/collections/_superusers/auth-with-password",
            json={"identity": settings.pocketbase_admin_email, "password": settings.pocketbase_admin_password},
        )
        r.raise_for_status()
        _client.headers.update({
            "Authorization": r.json()["token"],
            "Content-Type": "application/json",
        })
    return _client


# --- Auth ---

async def pb_login(email: str, password: str) -> dict:
    async with AsyncClient(base_url=settings.pocketbase_url) as client:
        r = await client.post(
            "/api/collections/users/auth-with-password",
            json={"identity": email, "password": password},
        )
        if r.status_code != 200:
            logger.warning("Login failed for %s: HTTP %d - %s", email, r.status_code, r.text[:200])
            body = r.json()
            msg = body.get("message", "Credenciales inválidas")
            raise HTTPException(status_code=401, detail=msg)
        return r.json()


async def pb_register(email: str, password: str, password_confirm: str, name: str = "") -> dict:
    async with AsyncClient(base_url=settings.pocketbase_url) as client:
        r = await client.post(
            "/api/collections/users/records",
            json={
                "email": email,
                "password": password,
                "passwordConfirm": password_confirm,
                "name": name,
            },
        )
        if r.status_code not in (200, 201):
            logger.warning("Register failed for %s: HTTP %d - %s", email, r.status_code, r.text[:300])
            body = r.json()
            msg = body.get("message", "Error al registrar")
            raise HTTPException(status_code=r.status_code, detail=msg)
        user = r.json()
    # Auto-login after register
    return await pb_login(email, password)


async def verify_pb_token(token: str) -> dict:
    async with AsyncClient(base_url=settings.pocketbase_url) as client:
        r = await client.post(
            "/api/collections/users/auth-refresh",
            headers={"Authorization": token},
        )
        if r.status_code != 200:
            raise HTTPException(status_code=401, detail="Token inválido o expirado")
        return r.json()["record"]


# --- FCM ---

async def register_fcm_token(user_id: str, token: str, platform: str = "android") -> dict:
    c = await _get_client()
    r = await c.post(
        "/api/collections/fcm_tokens/records",
        json={
            "user_id": user_id,
            "token": token,
            "platform": platform,
        },
    )
    if r.status_code not in (200, 201):
        # Try update if already exists
        existing = await c.get(
            "/api/collections/fcm_tokens/records",
            params={"filter": f'token="{token}"'},
        )
        if existing.status_code == 200:
            items = existing.json().get("items", [])
            if items:
                r = await c.patch(
                    f"/api/collections/fcm_tokens/records/{items[0]['id']}",
                    json={"user_id": user_id, "platform": platform},
                )
                r.raise_for_status()
                return r.json()
        raise HTTPException(status_code=400, detail="Error al registrar token FCM")
    return r.json()


async def get_project(project_id: str) -> dict | None:
    try:
        c = await _get_client()
        r = await c.get(f"/api/collections/projects/records/{project_id}")
        r.raise_for_status()
        return r.json()
    except Exception:
        return None


async def get_messages(session_id: str, limit: int = 50) -> list[dict]:
    c = await _get_client()
    r = await c.get(
        "/api/collections/messages/records",
        params={
            "filter": f'session_id="{session_id}"',
            "page": 1,
            "perPage": limit,
            "expand": "tool_calls_via_message_id",
        },
    )
    r.raise_for_status()
    items = r.json()["items"]
    for item in items:
        expanded = item.get("expand", {})
        tc_list = expanded.get("tool_calls_via_message_id", [])
        item["tool_calls"] = [
            {
                "id": tc.get("id"),
                "tool": tc.get("tool_name"),
                "args": tc.get("arguments", {}),
                "result": tc.get("result"),
                "isRunning": tc.get("status") == "running"
            }
            for tc in tc_list
        ]
    return items


async def create_session(title: str = "Nuevo chat") -> dict:
    c = await _get_client()
    r = await c.post(
        "/api/collections/sessions/records",
        json={"title": title},
    )
    r.raise_for_status()
    return r.json()


async def get_or_create_session(session_id: str) -> dict:
    existing = await get_session(session_id)
    if existing:
        return existing
    return await create_session()


async def save_message(session_id: str, role: str, content: str, attachments: list[str] | None = None) -> dict:
    c = await _get_client()
    r = await c.post(
        "/api/collections/messages/records",
        json={"session_id": session_id, "role": role, "content": content, "attachments": attachments or []},
    )
    r.raise_for_status()
    # Touch the session so its "updated" timestamp refreshes (used for recency sorting)
    try:
        await c.patch(f"/api/collections/sessions/records/{session_id}", json={})
    except Exception:
        pass
    return r.json()


async def create_tool_call(message_id: str, tool_name: str, arguments: dict) -> dict:
    c = await _get_client()
    r = await c.post(
        "/api/collections/tool_calls/records",
        json={"message_id": message_id, "tool_name": tool_name, "arguments": arguments, "status": "running"},
    )
    r.raise_for_status()
    return r.json()


async def update_tool_call(tool_call_id: str, result: str, status: str = "completed"):
    c = await _get_client()
    r = await c.patch(
        f"/api/collections/tool_calls/records/{tool_call_id}",
        json={"result": result, "status": status},
    )
    r.raise_for_status()
    return r.json()


async def get_provider(provider_id: str) -> dict | None:
    try:
        c = await _get_client()
        r = await c.get(f"/api/collections/providers/records/{provider_id}")
        r.raise_for_status()
        return r.json()
    except Exception:
        return None


async def get_session(session_id: str) -> dict | None:
    try:
        c = await _get_client()
        r = await c.get(f"/api/collections/sessions/records/{session_id}")
        r.raise_for_status()
        return r.json()
    except Exception:
        return None


async def list_sessions(limit: int = 50) -> list[dict]:
    c = await _get_client()
    r = await c.get(
        "/api/collections/sessions/records",
        params={"perPage": limit, "sort": "-updated"},
    )
    r.raise_for_status()
    return r.json()["items"]


async def delete_session(session_id: str):
    c = await _get_client()
    r = await c.delete(f"/api/collections/sessions/records/{session_id}")
    r.raise_for_status()


async def update_session_summary(session_id: str, summary: str):
    c = await _get_client()
    r = await c.patch(
        f"/api/collections/sessions/records/{session_id}",
        json={"context_summary": summary},
    )
    r.raise_for_status()
    return r.json()


async def update_session_title(session_id: str, title: str):
    c = await _get_client()
    r = await c.patch(
        f"/api/collections/sessions/records/{session_id}",
        json={"title": title},
    )
    r.raise_for_status()
    return r.json()


async def list_providers() -> list[dict]:
    c = await _get_client()
    r = await c.get("/api/collections/providers/records", params={"perPage": 100})
    r.raise_for_status()
    return r.json()["items"]


async def create_provider(data: dict) -> dict:
    c = await _get_client()
    r = await c.post("/api/collections/providers/records", json=data)
    r.raise_for_status()
    return r.json()


async def update_provider(provider_id: str, data: dict) -> dict:
    c = await _get_client()
    r = await c.patch(f"/api/collections/providers/records/{provider_id}", json=data)
    r.raise_for_status()
    return r.json()


async def delete_provider(provider_id: str):
    c = await _get_client()
    r = await c.delete(f"/api/collections/providers/records/{provider_id}")
    r.raise_for_status()


async def list_projects() -> list[dict]:
    c = await _get_client()
    r = await c.get("/api/collections/projects/records", params={"perPage": 100})
    r.raise_for_status()
    return r.json()["items"]


async def create_project(data: dict) -> dict:
    c = await _get_client()
    r = await c.post("/api/collections/projects/records", json=data)
    r.raise_for_status()
    return r.json()


async def update_project(project_id: str, data: dict) -> dict:
    c = await _get_client()
    r = await c.patch(f"/api/collections/projects/records/{project_id}", json=data)
    r.raise_for_status()
    return r.json()


async def delete_project(project_id: str):
    c = await _get_client()
    r = await c.delete(f"/api/collections/projects/records/{project_id}")
    r.raise_for_status()


async def list_mcp_servers(only_active: bool = True) -> list[dict]:
    c = await _get_client()
    params = {"perPage": 100}
    if only_active:
        params["filter"] = "is_active=True"
    r = await c.get("/api/collections/mcp_servers/records", params=params)
    r.raise_for_status()
    return r.json()["items"]


async def get_mcp_server(server_id: str) -> dict | None:
    try:
        c = await _get_client()
        r = await c.get(f"/api/collections/mcp_servers/records/{server_id}")
        r.raise_for_status()
        return r.json()
    except Exception:
        return None


async def create_mcp_server(data: dict) -> dict:
    c = await _get_client()
    r = await c.post("/api/collections/mcp_servers/records", json=data)
    r.raise_for_status()
    return r.json()


async def update_mcp_server(server_id: str, data: dict) -> dict:
    c = await _get_client()
    r = await c.patch(f"/api/collections/mcp_servers/records/{server_id}", json=data)
    r.raise_for_status()
    return r.json()


async def delete_mcp_server(server_id: str):
    c = await _get_client()
    r = await c.delete(f"/api/collections/mcp_servers/records/{server_id}")
    r.raise_for_status()


async def list_skills(only_active: bool = True) -> list[dict]:
    c = await _get_client()
    params = {"perPage": 100}
    if only_active:
        params["filter"] = "is_active=True"
    r = await c.get("/api/collections/skills/records", params=params)
    r.raise_for_status()
    return r.json()["items"]


async def get_skill(skill_id: str) -> dict | None:
    try:
        c = await _get_client()
        r = await c.get(f"/api/collections/skills/records/{skill_id}")
        r.raise_for_status()
        return r.json()
    except Exception:
        return None


async def create_skill(data: dict) -> dict:
    c = await _get_client()
    r = await c.post("/api/collections/skills/records", json=data)
    r.raise_for_status()
    return r.json()


async def update_skill(skill_id: str, data: dict) -> dict:
    c = await _get_client()
    r = await c.patch(f"/api/collections/skills/records/{skill_id}", json=data)
    r.raise_for_status()
    return r.json()


async def delete_skill(skill_id: str):
    c = await _get_client()
    r = await c.delete(f"/api/collections/skills/records/{skill_id}")
    r.raise_for_status()
