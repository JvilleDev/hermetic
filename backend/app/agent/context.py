from dataclasses import dataclass


@dataclass
class AgentContext:
    session_id: str
    project_id: str | None = None
    project_name: str | None = None
    parent_directory: str | None = None
    context_summary: str | None = None
    provider_id: str | None = None
    model: str | None = None
