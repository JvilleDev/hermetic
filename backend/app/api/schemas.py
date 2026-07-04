from pydantic import BaseModel


class ChatRequest(BaseModel):
    session_id: str
    message: str
    project_id: str | None = None
    project_name: str | None = None
    parent_directory: str | None = None


class TokenEvent(BaseModel):
    text: str


class ToolStartEvent(BaseModel):
    tool: str
    args: dict


class ToolResultEvent(BaseModel):
    tool: str
    result: str


class SSEEvent(BaseModel):
    event: str
    data: dict
