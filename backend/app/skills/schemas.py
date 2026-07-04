from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Skill:
    id: str
    name: str
    description: str
    skill_type: str  # native | mcp | python | prompt
    source: str | None = None
    system_prompt_additions: str | None = None
    tool_defs: list[dict] | None = None
    tool_code: str | None = None
    mcp_server_id: str | None = None
    is_active: bool = True

    def to_tool_defs(self) -> list[dict]:
        return self.tool_defs or []
