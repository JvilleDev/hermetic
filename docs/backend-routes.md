# Backend — Rutas de la API

FastAPI corriendo en `0.0.0.0:8000`.

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/api/directory/tree` | Árbol del sistema de archivos (`path`, `depth`, `dirs_only`) |
| POST | `/api/chat/stream` | Chat vía SSE — cuerpo `{ session_id, message, project_id?, project_name?, parent_directory? }` |
| GET | `/api/sessions` | Listar sesiones |
| POST | `/api/sessions` | Crear sesión `{ title }` |
| GET | `/api/sessions/{id}` | Detalle de sesión |
| DELETE | `/api/sessions/{id}` | Eliminar sesión |
| GET | `/api/sessions/{id}/messages` | Mensajes de una sesión |
| GET | `/api/providers` | Listar providers |
| POST | `/api/providers` | Crear provider |
| PATCH | `/api/providers/{id}` | Editar provider |
| DELETE | `/api/providers/{id}` | Eliminar provider |
| GET | `/api/projects` | Listar proyectos |
| GET | `/api/projects/{id}` | Detalle de proyecto |
| POST | `/api/projects` | Crear proyecto |
| DELETE | `/api/projects/{id}` | Eliminar proyecto |
| GET | `/api/mcp/servers` | Listar servidores MCP |
| POST | `/api/mcp/servers` | Agregar servidor MCP |
| PATCH | `/api/mcp/servers/{id}` | Editar servidor MCP |
| DELETE | `/api/mcp/servers/{id}` | Eliminar servidor MCP |
| GET | `/api/mcp/tools` | Listar herramientas MCP disponibles |
| GET | `/api/skills` | Listar skills |
| GET | `/api/skills/{id}` | Detalle de skill |
| PATCH | `/api/skills/{id}` | Editar skill |
| DELETE | `/api/skills/{id}` | Eliminar skill |

## Eventos SSE (Chat)

| Evento | Datos | Descripción |
|--------|-------|-------------|
| `session` | `{ session_id }` | ID de sesión (real) |
| `provider` | `{ model }` | Modelo seleccionado |
| `thinking` | `{ content }` | Razonamiento interno |
| `token` | `{ text }` | Token de respuesta |
| `tool_start` | `{ tool, args }` | Inicio de tool call |
| `tool_result` | `{ tool, result }` | Resultado de tool call |
| `done` | `{}` | Fin del stream |
