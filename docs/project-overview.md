# Hermetic — Visión General

**Hermetic** es un motor de IA soberano personal. Consiste en un backend FastAPI con un agente autónomo y una app nativa Android.

## Stack

- **Backend**: Python 3.10+, FastAPI, LiteLLM, MCP SDK, httpx
- **Base de datos**: PocketBase (alojada en `db-2.jville.dev`)
- **Cliente móvil**: Kotlin, Jetpack Compose, Hilt, OkHttp SSE
- **Infraestructura**: Tailscale (mesh VPN), desplegado en VPS OVH

## Arquitectura

```
App Android (Kotlin/Compose)
       |
       | SSE (Server-Sent Events)
       v
FastAPI (Python)
  ├── Agent Engine (LiteLLM + tool loop)
  ├── MCP Manager (conexiones stdio/sse)
  ├── Skill Registry (skills desde DB)
  ├── Tools nativos (terminal, files, git)
  └── DB Client (PocketBase vía REST)
```

## Fases

1. **Infraestructura soberana** — Tailscale + PocketBase en Docker
2. **Motor de agente** — FastAPI + LiteLLM + tool calling + SSE streaming
3. **App Android MVP** — Kotlin + Compose con chat SSE
4. **Tailscale embebido** — tsnet vía GoMobile para VPN auto-contenida
5. **Optimizaciones** — Voz, memoria vectorial, sandbox de terminal

## Repositorio

Monorepo con:
- `backend/` — API Python
- `android/` — App nativa Android
- `deploy/` — Esquemas y configs de despliegue
- `docs/` — Documentación
