# PocketBase — Esquema de Colecciones

## `users` (auth)
| Campo | Tipo |
|-------|------|
| id | text (PK) |
| email | email |
| password | password |
| name | text |
| avatar | file |

## `projects`
| Campo | Tipo |
|-------|------|
| name | text |
| path | text |
| description | text (opcional) |

## `sessions`
| Campo | Tipo |
|-------|------|
| title | text |
| project_id | relation -> projects |
| context_summary | text (opcional) |

## `messages`
| Campo | Tipo |
|-------|------|
| session_id | relation -> sessions |
| role | text (user/assistant) |
| content | text (opcional) |
| tool_calls | json (opcional) |

## `tool_calls`
| Campo | Tipo |
|-------|------|
| session_id | relation -> sessions |
| tool_name | text |
| args | json |
| result | text |

## `providers`
| Campo | Tipo |
|-------|------|
| name | text |
| provider_type | text (gemini/openai/anthropic) |
| api_key | text |
| default_model | text |
| is_active | bool |
| sort_order | number |

## `mcp_servers`
| Campo | Tipo |
|-------|------|
| name | text |
| transport_type | select (stdio/sse) |
| command | text (stdio) |
| args | json (stdio) |
| url | url (sse) |
| is_active | bool |

## `skills`
| Campo | Tipo |
|-------|------|
| name | text |
| description | text |
| skill_type | select (prompt/tool) |
| source | text |
| system_prompt_additions | text (opcional) |
| tool_defs | json (opcional) |
| tool_code | text (opcional) |
| mcp_server_id | relation -> mcp_servers (opcional) |
| is_active | bool |
