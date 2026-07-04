# Plan de Desarrollo Arquitectónico: Agente IA Soberano Personal

Este plan estructura la construcción de un ecosistema de IA soberano de extremo a extremo, dividiendo el trabajo en **5 fases lógicas e incrementales**. Se prioriza la seguridad desde el día uno mediante redes privadas de malla (Tailscale), la persistencia multimedia ligera (PocketBase), un motor de agente determinista (FastAPI + Pydantic AI/LangGraph) y una interfaz móvil nativa de alto rendimiento (Kotlin + Jetpack Compose).

---

## Arquitectura General del Sistema

```

[ Teléfono Android ]
│
├─► UI Nativa (Jetpack Compose @ 120Hz)
│
└─► Capa de Red (OkHttp + Interceptor tsnet/Tailscale)
│
(Túnel WireGuard Encriptado - Peer-to-Peer)
│
▼ [ VPS Soberano ]
├─► API Gateway & Agente (FastAPI + Pydantic AI / LangGraph)
│
│
│
├─► Ejecución de Herramientas (Tool Calling en Terminal VPS)
│
└─► Streaming SSE (/chat/stream)
│
└─► Persistencia & Multimedia (PocketBase / SQLite Embebido @ Puerto 8090)
```


---

## FASE 1: Infraestructura Soberana & Capa de Persistencia (VPS)
**Objetivo:** Establecer un entorno de servidor hermético, sin puertos expuestos al internet público, con la base de datos y almacenamiento multimedia listos para operar.

### 1. Configuración de Red Privada (Tailnet)
- [ ] **Instalación de Tailscale en VPS:** Configurar el demonio en el servidor Linux (`sudo tailscale up`) y autenticarlo en la cuenta personal.
- [ ] **Bloqueo de Puertos (Firewall UFW/iptables):** Verificar que los puertos `8090` (PocketBase) y `8000` (FastAPI) **no** sean accesibles desde internet público. Solo deben responder en la interfaz privada `tailscale0` (IPs `100.x.y.z`).
- [ ] **DNS Mágico (MagicDNS):** Habilitar MagicDNS en el panel de Tailscale para acceder al servidor mediante un nombre estable (ej. `mi-vps.tailnet.ts.net`) en lugar de recordar IPs.

### 2. Despliegue de PocketBase (Docker)
- [ ] **Contenedor Docker:** Montar PocketBase en Docker con un volumen persistente (`/pb_data`) para la base de datos SQLite embebida y archivos adjuntos.
- [ ] **Esquema Relacional (Colecciones):**
  - `users`: Autenticación del usuario administrador único.
  - `sessions`: Agrupación de hilos de conversación (`title`, `user_id`, `created_at`).
  - `messages`: Registro del historial (`session_id`, `role` [user/assistant/tool], `content`, `attachments` [Storage]).
  - `tool_calls`: Auditoría de ejecución en el servidor (`message_id`, `tool_name`, `arguments` [JSON], `result` [Text/JSON], `status` [running/completed/error]).

---

## FASE 2: Motor del Agente Custom & Tool Calling (Backend Python)
**Objetivo:** Crear el cerebro orquestador en Python que procese mensajes, consulte el historial en PocketBase, decida e invoque herramientas locales, y emita respuestas en streaming.

### 1. Núcleo de la API REST & SSE (FastAPI)
- [ ] **Estructura del Proyecto:** Inicializar proyecto con FastAPI, `uvicorn` y `pydantic-settings` para la gestión de variables de entorno (claves API de LLMs, URL de PocketBase).
- [ ] **Cliente PocketBase SDK:** Integrar la librería oficial de Python para autenticar el backend contra PocketBase de forma interna.
- [ ] **Gestión de Contexto:** Implementar lógica para recuperar los últimos *N* mensajes de la colección `messages` de una sesión para pasarlos como contexto al LLM.

### 2. Motor de Agente (Pydantic AI / LangGraph)
- [ ] **Definición del System Prompt:** Diseñar directrices estrictas de comportamiento y respuesta del asistente soberano.
- [ ] **Registro de Herramientas (Tools):** Programar las primeras herramientas controladas de ejecución local:
  - `get_system_metrics()`: Devuelve uso de CPU, RAM y disco del VPS mediante `psutil`.
  - `run_docker_command()`: Permite listar, reiniciar o inspeccionar contenedores Docker del VPS de forma acotada.
  - `read_vps_file()`: Lee contenidos de archivos dentro de directorios permitidos (sandbox).
- [ ] **Auditoría Transaccional:** Antes de ejecutar una herramienta, crear un registro en la colección `tool_calls` con estado `running`. Al finalizar, actualizar con el `result` y estado `completed` o `error`.

### 3. Streaming de Salida (Server-Sent Events)
- [ ] **Endpoint `/api/chat/stream`:** Implementar generador asíncrono (`StreamingResponse`) con formato SSE (`text/event-stream`).
- [ ] **Payloads Tipados:** Emitir eventos diferenciados para facilitar el renderizado en la UI móvil:
  - `event: token` -> `data: {"text": "..."}` (Fragmentos de texto del LLM).
  - `event: tool_start` -> `data: {"tool": "run_docker_command", "args": {...}}`.
  - `event: tool_result` -> `data: {"tool": "run_docker_command", "status": "completed"}`.

---

## FASE 3: App Nativa Android - MVP Capa de Red & UI (Kotlin + Compose)
**Objetivo:** Desarrollar el cliente Android nativo asumiendo que el dispositivo cuenta temporalmente con la app oficial de Tailscale conectada de fondo (MVP rápido).

### 1. Arquitectura de Red & Modelos de Datos
- [ ] **Cliente HTTP (Retrofit + OkHttp):** Configurar cliente base apuntando a la IP/DNS privado de Tailscale (`http://100.x.y.z:8000` y `8090`).
- [ ] **Cliente PocketBase (REST):** Implementar llamadas para iniciar sesión, listar sesiones previas de `sessions` y subir imágenes/audios al módulo Storage antes de iniciar el chat.
- [ ] **Streaming SSE (`OkHttp EventSource`):** Conectar el listener para procesar los eventos `token`, `tool_start` y `tool_result` emitidos por FastAPI en tiempo real.

### 2. Gestión de Estado (Modern Android Development)
- [ ] **ChatViewModel (`StateFlow`):** Modelar el estado inmutable de la pantalla de chat (`ChatUiState` conteniendo lista de mensajes, estado de carga, indicador de grabación de voz y herramienta en ejecución).
- [ ] **Mutación Token a Token:** Añadir cada fragmento recibido del SSE directamente al último mensaje del asistente en el `StateFlow` para lograr fluidez visual sin bloquear el hilo principal.

### 3. Interfaz Gráfica Nativa (Jetpack Compose)
- [ ] **Burbujas de Chat Dinámicas:** Componentes que cambian de diseño según `role` (`user`, `assistant`, `system`) y muestran archivos adjuntos descargados de PocketBase.
- [ ] **Renderizador Markdown:** Integrar librería (`Compose-Rich-Editor` o `Markwon`) para formatear bloques de código con resaltado de sintaxis, listas y tablas.
- [ ] **Tarjeta Desplegable de Tool Calling:** Componente visual interactivo que indique animadamente "⚙️ Ejecutando herramienta en VPS: [nombre_herramienta]...", permitiendo desplegar los argumentos JSON y el resultado final.

---

## FASE 4: Soberanía del Cliente - Integración de Tailscale Embebido (`tsnet`)
**Objetivo:** Eliminar la dependencia de la aplicación externa de Tailscale en Android, incrustando el nodo VPN directamente dentro del APK para una experiencia "mágica" de un solo clic.

### 1. Compilación e Integración de GoMobile / `tsnet`
- [ ] **Compilación de Librería nativa:** Crear un wrapper en Go utilizando `tsnet` (Tailscale as a Library) y compilarlo en un archivo `.aar` (Android Archive) mediante `gomobile bind`.
- [ ] **Configuración del Motor en Espacio de Usuario:** Inicializar el nodo embebido al arrancar la app en Android en un hilo de fondo (sin solicitar permisos de VPN al sistema operativo).

### 2. Autenticación y Enrutamiento Interno
- [ ] **Método Auth Key:** Generar una clave de autenticación persistente en el panel de Tailscale y almacenarla de forma segura mediante `EncryptedSharedPreferences` en Kotlin.
- [ ] **Enrutamiento HTTP sobre Socket Interno:** Configurar el `Dispatcher` o `Proxy` de OkHttp y Ktor para que todas las peticiones dirigidas a la red privada viajen a través del socket en memoria expuesto por `tsnet`.

---

## FASE 5: Optimizaciones Avanzadas & Consideraciones Extra
**Objetivo:** Llevar el proyecto de funcional a una herramienta de productividad diaria indestructible.

- [ ] **Entrada de Voz Nativa (Whisper / Local Audio):** Aprovechar la API `AudioRecord` de Android para grabar notas de voz en alta fidelidad (AAC/OPUS), subirlas a PocketBase y pasárselas al agente para transcripción automática con Whisper.
- [ ] **Manejo de Desconexión de Red:** Implementar un `NetworkCallback` en Android que detecte caídas de red o pérdida de conectividad con la malla privada, pausando el streaming y mostrando un banner informativo en Compose.
- [ ] **Memoria a Largo Plazo (Vectorial):** Integrar **pgvector** o **Qdrant** como contenedor docker auxiliar en el VPS para indexar reflexiones pasadas o resúmenes periódicos de los chats, permitiendo al agente buscar proactivamente en conversaciones de semanas atrás.
- [ ] **Seguridad del Terminal (Sandbox):** Ejecutar cualquier script Bash o comando experimental llamado por el LLM dentro de un sub-contenedor Docker aislado o usando permisos restringidos del sistema (`nobody`/cgroups) para prevenir modificaciones accidentales críticas en el VPS principal.
