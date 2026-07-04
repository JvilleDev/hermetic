from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncGenerator

from litellm import acompletion

logger = logging.getLogger(__name__)

from app.agent.context import AgentContext
from app.agent.prompts import build_system_prompt
from app.db.client import (
    save_message,
    update_session_summary,
    create_skill as db_create_skill,
    create_tool_call,
    update_tool_call,
    update_session_title,
    get_session,
    get_messages,
)
from app.mcp.manager import MCPManager
from app.providers.schemas import ProviderConfig
from app.providers.service import resolve_provider
from app.providers.tokenizer import count_tokens, get_context_limit, COMPACTION_THRESHOLD
from app.skills.registry import SkillRegistry
from app.tools.terminal import run_command as terminal_run
from app.tools.files import read_file, write_file, list_directory
from app.tools.git import git_status, git_log, git_diff

MAX_TOOL_TURNS = 10

NATIVE_TOOL_DEFS = [
    {
        "type": "function",
        "function": {
            "name": "terminal_run",
            "description": "Ejecuta un comando bash en el VPS. Usa el parent_directory del proyecto como cwd.",
            "parameters": {
                "type": "object",
                "properties": {
                    "command": {
                        "type": "string",
                        "description": "Comando bash a ejecutar",
                    },
                },
                "required": ["command"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "Lee el contenido de un archivo en el VPS.",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Ruta absoluta del archivo",
                    },
                },
                "required": ["path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "write_file",
            "description": "Escribe contenido en un archivo del VPS.",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Ruta absoluta del archivo",
                    },
                    "content": {
                        "type": "string",
                        "description": "Contenido a escribir",
                    },
                },
                "required": ["path", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_directory",
            "description": "Lista los archivos de un directorio en el VPS.",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Ruta absoluta del directorio",
                    },
                },
                "required": ["path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "git_status",
            "description": "Muestra el estado del repositorio Git en el proyecto activo.",
            "parameters": {
                "type": "object",
                "properties": {},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "git_log",
            "description": "Muestra el historial reciente de commits Git.",
            "parameters": {
                "type": "object",
                "properties": {
                    "count": {
                        "type": "integer",
                        "description": "N\u00famero de commits a mostrar (default 10)",
                    },
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "git_diff",
            "description": "Muestra los cambios sin staged en el repositorio Git.",
            "parameters": {
                "type": "object",
                "properties": {},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_skill",
            "description": "Crea una nueva skill persistente. La skill puede contener tool_defs y código Python ejecutable. Útil para añadir herramientas personalizadas al asistente.",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Nombre único de la skill",
                    },
                    "description": {
                        "type": "string",
                        "description": "Descripción de lo que hace",
                    },
                    "skill_type": {
                        "type": "string",
                        "enum": ["native", "python", "prompt"],
                        "description": "Tipo de skill: python para código ejecutable, prompt para instrucciones de sistema",
                    },
                    "system_prompt_additions": {
                        "type": "string",
                        "description": "Instrucciones adicionales para el prompt de sistema",
                    },
                    "tool_defs": {
                        "type": "string",
                        "description": "JSON array con definiciones de herramientas en formato OpenAI function calling",
                    },
                    "tool_code": {
                        "type": "string",
                        "description": "Código Python que implementa la(s) herramienta(s). Recibe args: dict y ctx: AgentContext. Debe retornar str.",
                    },
                },
                "required": ["name", "description", "skill_type"],
            },
        },
    },
]

NATIVE_TOOL_MAP = {
    "terminal_run": lambda args, ctx: terminal_run(
        args["command"], cwd=ctx.parent_directory
    ),
    "read_file": lambda args, ctx: read_file(args["path"]),
    "write_file": lambda args, ctx: write_file(args["path"], args["content"]),
    "list_directory": lambda args, ctx: list_directory(args["path"]),
    "git_status": lambda args, ctx: git_status(cwd=ctx.parent_directory),
    "git_log": lambda args, ctx: git_log(cwd=ctx.parent_directory, count=args.get("count", 10)),
    "git_diff": lambda args, ctx: git_diff(cwd=ctx.parent_directory),
}


def _load_skill_code(code: str):
    exec_globals = {}
    exec(code, exec_globals)
    return exec_globals


class AgentEngine:
    def __init__(self, mcp_manager: MCPManager | None = None, skill_registry: SkillRegistry | None = None):
        self._provider: ProviderConfig | None = None
        self.mcp_manager = mcp_manager or MCPManager()
        self.skill_registry = skill_registry or SkillRegistry()

    async def _get_provider(self, project_id: str | None, provider_id: str | None = None) -> ProviderConfig:
        return await resolve_provider(project_id=project_id, provider_id=provider_id)

    async def _compact_context(
        self, oai_messages: list[dict], provider: ProviderConfig, ctx: AgentContext, model_str: str
    ) -> list[dict]:
        context_limit = get_context_limit(model_str, provider.max_context_tokens)
        total = count_tokens(model_str, oai_messages)
        threshold = int(context_limit * COMPACTION_THRESHOLD)

        if total <= threshold:
            return oai_messages

        system = oai_messages[0]
        if len(oai_messages) <= 4:
            return oai_messages

        recent = oai_messages[-3:]
        to_summarize = oai_messages[1:-3]

        summary_text = "\n".join(
            f"{m['role']}: {m.get('content', '')}"
            for m in to_summarize
            if m.get("content")
        )

        if not summary_text.strip():
            return [system] + recent

        try:
            response = await acompletion(
                model=model_str,
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "Resume la siguiente conversación de forma concisa. "
                            "Conserva decisiones clave, contexto del proyecto, "
                            "y toda la información relevante para continuar la conversación."
                        ),
                    },
                    {"role": "user", "content": summary_text},
                ],
                api_key=provider.api_key,
                api_base=provider.base_url or None,
                stream=False,
                max_tokens=1024,
            )
            summary = response.choices[0].message.content
        except Exception:
            summary = "[Conversación anterior truncada por límite de contexto]"

        await update_session_summary(ctx.session_id, summary)

        return [
            system,
            {"role": "system", "content": f"Resumen de la conversación anterior:\n{summary}"},
        ] + recent

    async def _build_tools(self) -> list[dict]:
        tools = list(NATIVE_TOOL_DEFS)

        try:
            mcp_tools = await self.mcp_manager.list_tools()
            for t in mcp_tools:
                tools.append({
                    "type": "function",
                    "function": {
                        "name": f"mcp_{t['_mcp_server_id']}_{t['name']}",
                        "description": f"[MCP:{t['_mcp_server_name']}] {t.get('description', '')}",
                        "parameters": t.get("inputSchema", {}),
                    },
                })
        except Exception:
            pass

        try:
            skill_tool_defs = self.skill_registry.get_all_tool_defs()
            tools.extend(skill_tool_defs)
        except Exception:
            pass

        return tools

    async def stream_chat(
        self,
        messages: list[dict],
        ctx: AgentContext,
    ) -> AsyncGenerator[dict, None]:
        provider = await self._get_provider(ctx.project_id, provider_id=ctx.provider_id)
        # Use the model explicitly requested by the client, or fall back to provider's default
        resolved_model = provider.litellm_model_for(ctx.model)

        system_prompt = build_system_prompt(ctx.project_name, ctx.parent_directory)
        if ctx.context_summary:
            system_prompt += (
                f"\n\nResumen de la conversación anterior:\n{ctx.context_summary}"
            )

        try:
            skill_additions = self.skill_registry.get_all_system_prompt_additions()
            if skill_additions:
                system_prompt += f"\n\n{skill_additions}"
        except Exception:
            pass

        oai_messages: list[dict] = [{"role": "system", "content": system_prompt}]
        for msg in messages:
            role = msg.get("role")
            content = msg.get("content")
            tool_calls_list = msg.get("tool_calls", [])

            if role == "user":
                oai_messages.append({"role": "user", "content": content})
            elif role == "assistant":
                if tool_calls_list:
                    assistant_tc = []
                    for tc in tool_calls_list:
                        assistant_tc.append({
                            "id": tc.get("id"),
                            "type": "function",
                            "function": {
                                "name": tc.get("tool"),
                                "arguments": json.dumps(tc.get("args")) if isinstance(tc.get("args"), dict) else tc.get("args"),
                            }
                        })
                    oai_messages.append({
                        "role": "assistant",
                        "content": content or None,
                        "tool_calls": assistant_tc
                    })
                    for tc in tool_calls_list:
                        if tc.get("result") is not None:
                            oai_messages.append({
                                "role": "tool",
                                "tool_call_id": tc.get("id"),
                                "content": tc.get("result")
                            })
                else:
                    oai_messages.append({"role": "assistant", "content": content or None})

        oai_messages = await self._compact_context(oai_messages, provider, ctx, resolved_model)

        tools = await self._build_tools()

        yield {
            "type": "provider",
            "data": {"model": ctx.model or provider.default_model, "provider": provider.name},
        }

        for turn in range(MAX_TOOL_TURNS):
            response = await acompletion(
                model=resolved_model,
                messages=oai_messages,
                api_key=provider.api_key,
                api_base=provider.base_url or None,
                tools=tools if tools else None,
                stream=True,
            )

            full_text = ""
            thinking_text = ""
            tool_calls: dict[int, dict] = {}

            async for chunk in response:
                choice = chunk.choices[0]
                delta = choice.delta

                if hasattr(delta, "reasoning_content") and delta.reasoning_content:
                    thinking_text += delta.reasoning_content
                    yield {"type": "thinking", "data": {"text": delta.reasoning_content}}

                if delta.content:
                    full_text += delta.content
                    yield {"type": "token", "data": {"text": delta.content}}

                if delta.tool_calls:
                    for tc in delta.tool_calls:
                        idx = tc.index
                        if idx not in tool_calls:
                            tool_calls[idx] = {"id": "", "name": "", "arguments": ""}
                        if tc.id:
                            tool_calls[idx]["id"] = tc.id
                        if tc.function:
                            if tc.function.name:
                                tool_calls[idx]["name"] = tc.function.name
                            if tc.function.arguments:
                                tool_calls[idx]["arguments"] += tc.function.arguments

            if not tool_calls:
                if full_text:
                    await save_message(ctx.session_id, "assistant", full_text)
                break

            # Save the assistant message to PocketBase
            try:
                msg_record = await save_message(ctx.session_id, "assistant", full_text)
                message_id = msg_record["id"]
            except Exception:
                message_id = None

            # Create PocketBase tool_call records and update our local IDs
            for idx in sorted(tool_calls):
                tc_data = tool_calls[idx]
                if message_id:
                    try:
                        args = json.loads(tc_data["arguments"])
                        pb_tc = await create_tool_call(message_id, tc_data["name"], args)
                        tc_data["id"] = pb_tc["id"]
                    except Exception:
                        pass

            assistant_tc = []
            for idx in sorted(tool_calls):
                tc_data = tool_calls[idx]
                assistant_tc.append({
                    "id": tc_data["id"],
                    "type": "function",
                    "function": {
                        "name": tc_data["name"],
                        "arguments": tc_data["arguments"],
                    },
                })

            oai_messages.append({
                "role": "assistant",
                "content": full_text or None,
                "tool_calls": assistant_tc,
            })

            for idx in sorted(tool_calls):
                tc_data = tool_calls[idx]
                args = json.loads(tc_data["arguments"])

                yield {
                    "type": "tool_start",
                    "data": {"tool": tc_data["name"], "args": args},
                }

                result = await self._execute_tool(tc_data["name"], args, ctx)

                yield {
                    "type": "tool_result",
                    "data": {"tool": tc_data["name"], "result": result},
                }

                # Update the tool call record in PocketBase with execution result
                if message_id and tc_data["id"]:
                    try:
                        status = "error" if result.startswith("Error") else "completed"
                        await update_tool_call(tc_data["id"], result, status)
                    except Exception:
                        pass

                oai_messages.append({
                    "role": "tool",
                    "tool_call_id": tc_data["id"],
                    "content": result,
                })

        try:
            await self._auto_generate_session_title(ctx.session_id, provider)
        except Exception as e:
            logger.warning("Error generando título automático: %s", e)

    async def _auto_generate_session_title(self, session_id: str, provider: ProviderConfig):
        session = await get_session(session_id)
        if not session or session.get("title") != "Nuevo chat":
            return

        history = await get_messages(session_id, limit=5)
        user_msg = ""
        assistant_msg = ""
        for m in history:
            if m.get("role") == "user" and not user_msg:
                user_msg = m.get("content", "")
            elif m.get("role") == "assistant" and not assistant_msg:
                assistant_msg = m.get("content", "")

        if not user_msg:
            return

        prompt = (
            "Genera un título corto y descriptivo (máximo 5 palabras, sin comillas, directo y en español) "
            "para una conversación que empieza con la siguiente interacción:\n\n"
            f"Usuario: {user_msg}\n"
            f"Asistente: {assistant_msg}"
        )

        response = await acompletion(
            model=resolved_model,
            messages=[
                {"role": "system", "content": "Eres un asistente que genera títulos cortos y descriptivos para chats."},
                {"role": "user", "content": prompt}
            ],
            api_key=provider.api_key,
            api_base=provider.base_url or None,
            stream=False,
            max_tokens=50,
        )
        message = response.choices[0].message
        content = message.content
        finish_reason = response.choices[0].finish_reason
        if not content:
            logger.info(
                "Título vacío - finish_reason=%s, content=%s",
                finish_reason, content,
            )
        if content:
            title = content.strip().strip('"').strip("'").strip(".")
            if title:
                logger.info("Título generado: %s", title)
                await update_session_title(session_id, title)

    async def _execute_tool(self, name: str, args: dict, ctx: AgentContext) -> str:
        try:
            if name in NATIVE_TOOL_MAP:
                return str(NATIVE_TOOL_MAP[name](args, ctx))

            if name == "create_skill":
                return await self._handle_create_skill(args)

            if name.startswith("mcp_"):
                parts = name.split("_", 2)
                if len(parts) == 3:
                    _, server_id, tool_name = parts
                    return await self.mcp_manager.call_tool(server_id, tool_name, args)

            skill = self.skill_registry.get_by_name(name)
            if skill and skill.tool_code:
                ns = _load_skill_code(skill.tool_code)
                handler = ns.get("handle_tool")
                if handler:
                    return str(handler(args, ctx))
                return f"Error: Skill '{name}' has tool_code but no handle_tool function"

            return f"Error: Tool '{name}' not found"
        except Exception as e:
            return f"Error executing {name}: {e}"

    async def _handle_create_skill(self, args: dict) -> str:
        tool_defs_raw = args.get("tool_defs")
        if isinstance(tool_defs_raw, str):
            try:
                tool_defs = json.loads(tool_defs_raw)
            except json.JSONDecodeError:
                tool_defs = None
        else:
            tool_defs = tool_defs_raw

        data = {
            "name": args["name"],
            "description": args.get("description", ""),
            "skill_type": args.get("skill_type", "native"),
            "source": "generated",
            "system_prompt_additions": args.get("system_prompt_additions"),
            "tool_defs": tool_defs,
            "tool_code": args.get("tool_code"),
            "is_active": True,
        }

        try:
            record = await db_create_skill(data)
            await self.skill_registry.load_all()
            return f"Skill '{args['name']}' creada exitosamente (id: {record['id']})"
        except Exception as e:
            return f"Error al crear skill: {e}"
