from __future__ import annotations

from app.db.client import list_skills as db_list_skills
from app.skills.schemas import Skill


class SkillRegistry:
    def __init__(self):
        self._skills: dict[str, Skill] = {}

    async def load_all(self):
        records = await db_list_skills()
        for r in records:
            skill = Skill(
                id=r["id"],
                name=r.get("name", ""),
                description=r.get("description", ""),
                skill_type=r.get("skill_type", "native"),
                source=r.get("source"),
                system_prompt_additions=r.get("system_prompt_additions"),
                tool_defs=r.get("tool_defs"),
                tool_code=r.get("tool_code"),
                mcp_server_id=r.get("mcp_server_id"),
                is_active=r.get("is_active", True),
            )
            if skill.is_active:
                self._skills[skill.name] = skill

    def get_active_skills(self) -> list[Skill]:
        return [s for s in self._skills.values() if s.is_active]

    def get_by_name(self, name: str) -> Skill | None:
        return self._skills.get(name)

    def get_all_system_prompt_additions(self) -> str:
        parts = []
        for skill in self.get_active_skills():
            if skill.system_prompt_additions:
                parts.append(
                    f"[SKILL: {skill.name}]\n{skill.system_prompt_additions}"
                )
        return "\n\n".join(parts)

    def get_all_tool_defs(self) -> list[dict]:
        defs = []
        for skill in self.get_active_skills():
            defs.extend(skill.to_tool_defs())
        return defs
