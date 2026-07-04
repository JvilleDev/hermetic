from __future__ import annotations

import asyncio
import json
import os
from contextlib import asynccontextmanager

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


class MCPConnection:
    def __init__(self, record: dict):
        self.id = record["id"]
        self.name = record.get("name", "")
        self.description = record.get("description", "")
        self.transport = record.get("transport", "stdio")
        self.command = record.get("command")
        self.args = record.get("args") or []
        self.url = record.get("url")
        self.env = record.get("env") or {}
        self._session: ClientSession | None = None
        self._tools: list[dict] | None = None
        self._exit_stack = None

    async def connect(self):
        if self.transport == "stdio":
            env = {**os.environ, **self.env} if self.env else None
            params = StdioServerParameters(
                command=self.command,
                args=self.args,
                env=env,
            )
            self._exit_stack = asyncio.ExitStack()
            self._read, self._write = await self._exit_stack.enter_async_context(
                stdio_client(params)
            )
        elif self.transport == "sse":
            from mcp.client.sse import sse_client

            self._exit_stack = asyncio.ExitStack()
            self._read, self._write = await self._exit_stack.enter_async_context(
                sse_client(url=self.url)
            )
        else:
            raise ValueError(f"Unsupported transport: {self.transport}")

        self._session = await self._exit_stack.enter_async_context(
            ClientSession(self._read, self._write)
        )
        await self._session.initialize()

    async def list_tools(self) -> list[dict]:
        if self._session is None:
            await self.connect()
        if self._tools is None:
            result = await self._session.list_tools()
            self._tools = [
                {
                    "name": t.name,
                    "description": t.description,
                    "inputSchema": t.inputSchema,
                }
                for t in result.tools
            ]
        return self._tools

    async def call_tool(self, name: str, args: dict) -> str:
        if self._session is None:
            await self.connect()
        result = await self._session.call_tool(name, args)
        parts = []
        for content in result.content:
            if hasattr(content, "text"):
                parts.append(content.text)
            else:
                parts.append(str(content))
        return "\n".join(parts)

    async def close(self):
        if self._exit_stack:
            await self._exit_stack.aclose()
            self._session = None
            self._tools = None
            self._exit_stack = None


class MCPManager:
    def __init__(self):
        self._connections: dict[str, MCPConnection] = {}

    async def add_server(self, record: dict) -> MCPConnection:
        conn = MCPConnection(record)
        await conn.connect()
        self._connections[record["id"]] = conn
        return conn

    async def remove_server(self, server_id: str):
        conn = self._connections.pop(server_id, None)
        if conn:
            await conn.close()

    async def list_tools(self) -> list[dict]:
        tools = []
        for conn in self._connections.values():
            try:
                server_tools = await conn.list_tools()
                for t in server_tools:
                    t["_mcp_server_id"] = conn.id
                    t["_mcp_server_name"] = conn.name
                tools.extend(server_tools)
            except Exception as e:
                pass
        return tools

    async def call_tool(self, server_id: str, name: str, args: dict) -> str:
        conn = self._connections.get(server_id)
        if conn is None:
            return f"Error: MCP server '{server_id}' not connected"
        return await conn.call_tool(name, args)

    async def close_all(self):
        for conn in list(self._connections.values()):
            await conn.close()
        self._connections.clear()
