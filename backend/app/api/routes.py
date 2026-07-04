from __future__ import annotations

import json
from collections.abc import AsyncGenerator
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import StreamingResponse

from app.api.schemas import ChatRequest
from app.agent.engine import AgentEngine
from app.agent.context import AgentContext
from app.providers.models_catalog import get_models_for_provider_type, get_models_from_openai_compatible
from app.db.client import (
    save_message,
    get_messages,
    get_session,
    get_or_create_session,
    list_sessions,
    create_session,
    delete_session,
    list_providers,
    create_provider,
    update_provider,
    delete_provider,
    get_project,
    list_projects,
    create_project,
    update_project,
    delete_project,
    list_mcp_servers,
    create_mcp_server,
    update_mcp_server,
    delete_mcp_server,
    get_mcp_server,
    list_skills,
    get_skill,
    update_skill,
    delete_skill,
    pb_login,
    pb_register,
    register_fcm_token,
)
from app.services.notifier import send_release_notification
from app.mcp.manager import MCPManager
from app.skills.registry import SkillRegistry

mcp_manager = MCPManager()
skill_registry = SkillRegistry()
engine = AgentEngine(mcp_manager=mcp_manager, skill_registry=skill_registry)

router = APIRouter()


@router.on_event("startup")
async def startup():
    try:
        servers = await list_mcp_servers()
        for s in servers:
            try:
                await mcp_manager.add_server(s)
            except Exception:
                pass
    except Exception:
        pass
    try:
        await skill_registry.load_all()
    except Exception:
        pass


@router.on_event("shutdown")
async def shutdown():
    await mcp_manager.close_all()


# --- Auth ---

@router.post("/api/auth/login")
async def auth_login(data: dict):
    result = await pb_login(data["email"], data["password"])
    return {
        "token": result["token"],
        "user": result["record"],
    }


@router.post("/api/auth/register")
async def auth_register(data: dict):
    result = await pb_register(
        email=data["email"],
        password=data["password"],
        password_confirm=data.get("password_confirm", data["password"]),
        name=data.get("name", ""),
    )
    return {
        "token": result["token"],
        "user": result["record"],
    }


@router.get("/api/auth/me")
async def auth_me(request: Request):
    return {"user": request.state.user}


# --- Push Notifications ---

@router.post("/api/push/register-token")
async def register_fcm_endpoint(data: dict, request: Request):
    await register_fcm_token(
        user_id=request.state.user["id"],
        token=data["token"],
        platform=data.get("platform", "android"),
    )
    return {"status": "ok"}


# --- Release Webhook ---

@router.post("/api/push/notify-release")
async def notify_release(data: dict):
    secret = data.get("secret", "")
    if not settings.release_webhook_secret or secret != settings.release_webhook_secret:
        raise HTTPException(status_code=403, detail="Invalid secret")

    version = data.get("version", "unknown")
    release_url = data.get("release_url", "")

    await send_release_notification(version, release_url)
    return {"status": "ok", "notified": True}


# --- Health ---

@router.get("/health")
async def health():
    return {"status": "ok"}


# --- Directory ---

def _build_tree(path: Path, depth: int, dirs_only: bool) -> dict:
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"Path not found: {path}")
    if not path.is_dir():
        raise HTTPException(status_code=400, detail=f"Not a directory: {path}")

    result: dict = {
        "name": path.name,
        "path": str(path),
        "type": "directory",
        "children": [],
    }

    if depth <= 0:
        return result

    try:
        entries = sorted(path.iterdir(), key=lambda e: (not e.is_dir(), e.name.lower()))
    except PermissionError:
        result["children"] = None
        return result

    for entry in entries:
        if entry.name.startswith("."):
            continue
        if entry.is_dir():
            child = _build_tree(entry, depth - 1, dirs_only)
            result["children"].append(child)
        elif not dirs_only:
            result["children"].append({
                "name": entry.name,
                "path": str(entry),
                "type": "file",
            })

    return result


@router.get("/api/directory/tree")
async def directory_tree(
    path: str = Query("/dev", description="Root path to explore"),
    depth: int = Query(2, ge=1, le=5, description="How many levels deep"),
    dirs_only: bool = Query(True, description="Only show directories"),
):
    return _build_tree(Path(path), depth, dirs_only)


# --- Chat ---

@router.post("/api/chat/stream")
async def chat_stream(request: ChatRequest):
    session = await get_or_create_session(request.session_id)
    real_session_id = session["id"]

    await save_message(real_session_id, "user", request.message)

    ctx = AgentContext(
        session_id=real_session_id,
        project_id=request.project_id,
        project_name=request.project_name,
        parent_directory=request.parent_directory,
        context_summary=session.get("context_summary"),
        provider_id=request.provider_id,
        model=request.model,
    )

    return StreamingResponse(
        _stream_events(real_session_id, ctx),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


async def _stream_events(session_id: str, ctx: AgentContext) -> AsyncGenerator[str, None]:
    yield f"event: session\ndata: {json.dumps({'session_id': session_id})}\n\n"

    try:
        history = await get_messages(session_id)
        messages = [{"role": m["role"], "content": m["content"]} for m in history]

        async for event in engine.stream_chat(messages, ctx):
            yield f"event: {event['type']}\ndata: {json.dumps(event['data'])}\n\n"
    except Exception as e:
        import logging
        logger = logging.getLogger("api")
        logger.exception("Error during chat streaming")
        
        error_msg = str(e)
        if "RateLimitError" in error_msg or "429" in error_msg:
            error_msg = "Límite de peticiones de API excedido. Por favor, intenta de nuevo más tarde."
        elif "OpenAIError" in error_msg:
            error_msg = "Error en la comunicación con la IA. Por favor, intenta de nuevo."
        else:
            error_msg = f"Error inesperado: {error_msg}"
            
        yield f"event: error\ndata: {json.dumps({'message': error_msg})}\n\n"

    yield "event: done\ndata: {}\n\n"


# --- Sessions ---

@router.get("/api/sessions")
async def get_sessions(limit: int = Query(50, ge=1, le=100)):
    return await list_sessions(limit)


@router.post("/api/sessions")
async def create_session_endpoint(data: dict):
    return await create_session(data.get("title", "Nuevo chat"))


@router.get("/api/sessions/{session_id}")
async def get_session_detail(session_id: str):
    session = await get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return session


@router.delete("/api/sessions/{session_id}")
async def remove_session(session_id: str):
    await delete_session(session_id)
    return {"status": "deleted"}


# --- Messages ---

@router.get("/api/sessions/{session_id}/messages")
async def get_session_messages(session_id: str, limit: int = Query(50, ge=1, le=200)):
    return await get_messages(session_id, limit)


# --- Providers ---

@router.get("/api/providers")
async def get_providers():
    return await list_providers()


@router.get("/api/providers/with-models")
async def get_providers_with_models():
    """Return all providers, each enriched with a full list of available chat models."""
    raw = await list_providers()
    result = []
    for p in raw:
        provider_type = p.get("provider_type", "")
        base_url = p.get("base_url") or ""
        api_key = p.get("api_key", "")

        # Custom base_url → query the endpoint live (Ollama, LM Studio, etc.)
        if base_url and provider_type == "openai":
            models = await get_models_from_openai_compatible(base_url, api_key)
        else:
            models = get_models_for_provider_type(provider_type)

        # Fallback: always include the configured default_model
        default = p.get("default_model", "")
        if default and default not in models:
            models = [default] + models

        result.append({
            "id": p["id"],
            "name": p["name"],
            "provider_type": provider_type,
            "base_url": base_url or None,
            "default_model": default,
            "is_default": p.get("is_default", False),
            "is_active": p.get("is_active", True),
            "models": models,
        })
    return result


@router.post("/api/providers")
async def add_provider(data: dict):
    return await create_provider(data)


@router.patch("/api/providers/{provider_id}")
async def edit_provider(provider_id: str, data: dict):
    return await update_provider(provider_id, data)


@router.delete("/api/providers/{provider_id}")
async def remove_provider(provider_id: str):
    await delete_provider(provider_id)
    return {"status": "deleted"}


# --- Projects ---

@router.get("/api/projects")
async def get_projects():
    return await list_projects()


@router.get("/api/projects/{project_id}")
async def get_project_detail(project_id: str):
    project = await get_project(project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    return project


@router.post("/api/projects")
async def add_project(data: dict):
    return await create_project(data)


@router.patch("/api/projects/{project_id}")
async def patch_project(project_id: str, data: dict):
    return await update_project(project_id, data)


@router.delete("/api/projects/{project_id}")
async def remove_project(project_id: str):
    await delete_project(project_id)
    return {"status": "deleted"}


# --- MCP Servers ---

@router.get("/api/mcp/servers")
async def get_mcp_servers():
    return await list_mcp_servers()


@router.post("/api/mcp/servers")
async def add_mcp_server(data: dict):
    record = await create_mcp_server(data)
    try:
        await mcp_manager.add_server(record)
    except Exception:
        pass
    return record


@router.patch("/api/mcp/servers/{server_id}")
async def edit_mcp_server(server_id: str, data: dict):
    record = await update_mcp_server(server_id, data)
    await mcp_manager.remove_server(server_id)
    try:
        await mcp_manager.add_server(record)
    except Exception:
        pass
    return record


@router.delete("/api/mcp/servers/{server_id}")
async def remove_mcp_server(server_id: str):
    await mcp_manager.remove_server(server_id)
    await delete_mcp_server(server_id)
    return {"status": "deleted"}


@router.get("/api/mcp/tools")
async def get_mcp_tools():
    return await mcp_manager.list_tools()


# --- Skills ---

@router.get("/api/skills")
async def get_skills():
    return await list_skills()


@router.get("/api/skills/{skill_id}")
async def get_skill_detail(skill_id: str):
    skill = await get_skill(skill_id)
    if skill is None:
        raise HTTPException(status_code=404, detail="Skill not found")
    return skill


@router.patch("/api/skills/{skill_id}")
async def edit_skill(skill_id: str, data: dict):
    record = await update_skill(skill_id, data)
    await skill_registry.load_all()
    return record


@router.delete("/api/skills/{skill_id}")
async def remove_skill(skill_id: str):
    await delete_skill(skill_id)
    await skill_registry.load_all()
    return {"status": "deleted"}
