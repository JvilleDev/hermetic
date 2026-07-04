"""
Enumerate all chat-capable models available for each provider type.

For known providers (gemini, openai, anthropic) we query LiteLLM's static
model catalog and filter to mode=chat + /v1/chat/completions support.
For providers with a custom base_url (e.g. OpenAI-compatible endpoints like
Ollama or LM Studio) we hit the provider's /v1/models endpoint directly.
"""

import httpx
import litellm

# Provider type → litellm provider prefix used in model_cost keys
_PROVIDER_PREFIX: dict[str, str] = {
    "gemini": "gemini",
    "openai": "",          # openai models have no prefix in model_cost
    "anthropic": "anthropic",
}

# Known non-chat suffixes / substrings to exclude
_EXCLUDE_KEYWORDS = [
    "embed", "tts", "whisper", "image", "vision-only", "transcri",
    "imagen", "veo", "lyria", "robotics", "audio-only", "moderat",
    "instruct",  # text-completion style
]


def _is_chat_model(model_id: str, meta: dict) -> bool:
    """Return True if this litellm model entry is a plain chat model."""
    if meta.get("mode") != "chat":
        return False
    endpoints = meta.get("supported_endpoints", [])
    if endpoints and "/v1/chat/completions" not in endpoints:
        return False
    lower = model_id.lower()
    if any(kw in lower for kw in _EXCLUDE_KEYWORDS):
        return False
    return True


def get_models_for_provider_type(provider_type: str) -> list[str]:
    """Return sorted list of chat model names for a known provider type."""
    prefix = _PROVIDER_PREFIX.get(provider_type)
    if prefix is None:
        return []

    results: list[str] = []
    for model_id, meta in litellm.model_cost.items():
        litellm_provider = meta.get("litellm_provider", "")
        if litellm_provider != provider_type:
            continue
        if not _is_chat_model(model_id, meta):
            continue
        # Strip the "provider/" prefix so the UI shows clean model names.
        # e.g. "gemini/gemini-2.5-flash" → "gemini-2.5-flash"
        clean = model_id
        if prefix and clean.startswith(f"{prefix}/"):
            clean = clean[len(prefix) + 1:]
        results.append(clean)

    return sorted(set(results))


async def get_models_from_openai_compatible(base_url: str, api_key: str) -> list[str]:
    """Hit /v1/models on a custom OpenAI-compatible endpoint, filtered to text-only."""
    url = base_url.rstrip("/") + "/v1/models"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            r = await client.get(url, headers={"Authorization": f"Bearer {api_key}"})
            r.raise_for_status()
            data = r.json()
            models: list[str] = []
            for m in data.get("data", []):
                mid = m.get("id", "")
                lower = mid.lower()
                if any(kw in lower for kw in _EXCLUDE_KEYWORDS):
                    continue
                models.append(mid)
            return sorted(models)
    except Exception:
        return []
