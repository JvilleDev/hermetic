from app.providers.schemas import ProviderConfig
from app.db.client import _get_client


async def resolve_provider(project_id: str | None = None, provider_id: str | None = None) -> ProviderConfig:
    c = await _get_client()

    # Direct provider lookup takes highest priority (explicit selection by user)
    if provider_id:
        r = await c.get(f"/api/collections/providers/records/{provider_id}")
        return _parse_provider(r.json())

    if project_id:
        proj = await c.get(f"/api/collections/projects/records/{project_id}")
        proj = proj.json()
        proj_provider_id = proj.get("provider_id")
        if proj_provider_id:
            r = await c.get(f"/api/collections/providers/records/{proj_provider_id}")
            data = r.json()
            return _parse_provider(data)

    r = await c.get("/api/collections/providers/records", params={
        "filter": "is_default=True",
        "perPage": 1,
    })
    items = r.json().get("items", [])
    if not items:
        raise ValueError("No default provider found. Configure one in PocketBase.")
    return _parse_provider(items[0])


def _parse_provider(data: dict) -> ProviderConfig:
    return ProviderConfig(
        id=data["id"],
        name=data["name"],
        provider_type=data["provider_type"],
        api_key=data["api_key"],
        base_url=data.get("base_url") or None,
        default_model=data["default_model"],
        max_context_tokens=data.get("max_context_tokens") or None,
        is_active=data.get("is_active", True),
        is_default=data.get("is_default", False),
    )
