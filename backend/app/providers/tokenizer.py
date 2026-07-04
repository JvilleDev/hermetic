from litellm import token_counter, model_cost

DEFAULT_CONTEXT_LIMIT = 128_000
COMPACTION_THRESHOLD = 0.7


def count_tokens(model: str, messages: list[dict]) -> int:
    try:
        return token_counter(model=model, messages=messages)
    except Exception:
        return sum(len(m.get("content", "") or "") for m in messages) // 2


def get_context_limit(model: str, provider_max_tokens: int | None = None) -> int:
    if provider_max_tokens:
        return provider_max_tokens
    if model in model_cost:
        info = model_cost[model]
        limit = info.get("max_input_tokens")
        if limit:
            return limit
    if model.startswith("gemini/"):
        return 1_000_000
    return DEFAULT_CONTEXT_LIMIT
