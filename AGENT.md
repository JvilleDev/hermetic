🤖 Hermetic - Manual del Agente (AGENT.md)

1. 🧠 Rol y Contexto del Agente

Eres el Agente Principal de Desarrollo para Hermetic, un ecosistema de IA personal y soberano.
Tu objetivo es asistir en la construcción de este sistema, escribiendo código limpio, modular y adhiriéndote estrictamente a la arquitectura definida.

Naturaleza del Proyecto: Hermetic es un asistente de IA (LLM) que se ejecuta nativamente en un VPS (mediante systemd, sin Docker), con acceso real al sistema de archivos para operar en "Proyectos". El usuario interactúa mediante una app Android nativa. Toda la comunicación ocurre bajo una red privada peer-to-peer (Tailscale).

2. 📜 Directrices Inquebrantables de Arquitectura

Al proponer, refactorizar o escribir código para Hermetic, debes respetar las siguientes reglas:

Cero Exposición Pública: Asume siempre que la API y la base de datos están bajo una IP de Tailscale (100.x.y.z). No uses autenticación basada en JWT o OAuth complejos para la API interna, confía en la seguridad de la malla P2P.

Backend Nativo (Sin Docker): El motor de IA (FastAPI/Python) se ejecuta directamente en el host Linux. No propongas Dockerfiles para el backend. Usa pyproject.toml y systemd.

Consciencia Espacial (CWD Dinámico): Las herramientas de terminal y archivos del agente deben leer siempre el parent_directory de la colección projects en PocketBase e inyectarlo como el cwd (Current Working Directory).

Android 100% Nativo: Utiliza exclusivamente Kotlin y Jetpack Compose. Evita dependencias de UI pesadas de terceros; usa componentes estándar de Material 3 y flujos reactivos puros (StateFlow).

Streaming Obligatorio: Las respuestas del LLM y los eventos de tool_calling deben transmitirse vía Server-Sent Events (SSE).

3. 🛠️ Stack Tecnológico Autorizado

Persistencia / Multimedia: PocketBase (SQLite embebido, ejecutándose en el puerto 8090).

Backend de IA: Python 3.10+, FastAPI, Pydantic AI (o LangGraph), Uvicorn.

Cliente Móvil: Android, Kotlin, Jetpack Compose, OkHttp (con EventSource para SSE), Markwon / Compose-Rich-Editor (Markdown).

Infraestructura: Tailscale, systemd, scripts bash.

4. 🗺️ Plan de Desarrollo por Fases

El desarrollo se divide en 5 fases secuenciales. Como agente, debes ayudar a completar cada hito antes de pasar al siguiente.

Fase 1: Infraestructura y Persistencia (Estado: 🟡 Pendiente)

[ ] Desplegar PocketBase de forma nativa o servicio en el VPS (Puerto 8090).

[ ] Configurar Colecciones en PocketBase:

projects (name, parent_directory)

sessions (title, project_id [relation opcional])

messages (session_id, role, content, attachments)

tool_calls (message_id, tool_name, arguments, result, status)

[ ] Exportar el esquema inicial a deploy/pb_schema.json.

Fase 2: Motor del Agente Custom (Backend Python) (Estado: 🟡 Pendiente)

[ ] Setup del Proyecto: Crear la estructura backend/, configurar pyproject.toml y FastAPI.

[ ] Integración PocketBase: Escribir el cliente en Python para interactuar con la DB localmente.

[ ] Orquestador (Pydantic AI / LangGraph): Configurar el flujo de memoria y el System Prompt.

[ ] Herramientas Nativas (Tools):

terminal.py: Ejecutar comandos bash inyectando el parent_directory.

files.py: Leer/escribir archivos en el disco del VPS.

[ ] Endpoint SSE: Implementar /api/chat/stream emitiendo token, tool_start y tool_result.

Fase 3: Despliegue Global del Servidor (Estado: 🟡 Pendiente)

[ ] Script de Instalación: Escribir deploy/install.sh para crear el venv y hacer pip install ..

[ ] Servicio Systemd: Escribir deploy/hermetic.service para mantener el proceso vivo y accesible bajo el usuario del sistema operativo.

Fase 4: App Android MVP (Kotlin + Compose) (Estado: 🟡 Pendiente)

[ ] Capa de Red: Configurar OkHttp apuntando a la IP de Tailscale y conectar REST para PocketBase.

[ ] Listener SSE: Implementar la captura asíncrona del streaming del agente.

[ ] Estado Reactivo: Configurar el ChatViewModel con StateFlow para mutación token a token.

[ ] UI del Chat: Crear burbujas de mensaje para Usuario, Asistente y visualización (animada) de la ejecución de herramientas.

Fase 5: Soberanía Avanzada (Estado: 🟡 Opcional / Futuro)

[ ] Tailscale Embebido (tsnet): Integrar la librería GoMobile en Android para evitar depender de la app oficial de Tailscale.

[ ] Voz a Texto Nativo: Implementar grabación en Android y transcripción automática mediante el LLM o Whisper en el VPS.

5. 🔄 Protocolo de Interacción

Cuando el usuario pida desarrollar una nueva característica:

Revisa esta hoja de ruta.

Identifica en qué fase estamos.

Genera código modular, separando la lógica de UI de la lógica de red.

Explica siempre si el código debe ir en el directorio backend/, android/ o deploy/.
