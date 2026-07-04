from dataclasses import dataclass


@dataclass
class ProviderConfig:
    id: str
    name: str
    provider_type: str
    api_key: str
    base_url: str | None = None
    default_model: str = ""
    max_context_tokens: int | None = None
    is_active: bool = True
    is_default: bool = False

    @property
    def litellm_model(self) -> str:
        return f"{self.provider_type}/{self.default_model}"

    def litellm_model_for(self, model: str | None = None) -> str:
        """Return the litellm model string, using model override if provided."""
        m = model or self.default_model
        return f"{self.provider_type}/{m}"
