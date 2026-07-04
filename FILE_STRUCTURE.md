hermetic/
├── android/                  # App móvil nativa (Kotlin + Jetpack Compose)
│
├── backend/                  # Motor IA soberano en Python (FastAPI + Pydantic AI/LangGraph)
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py           # Punto de entrada de la API REST y SSE (/api/chat/stream)
│   │   ├── config.py         # Carga de variables de entorno (.env)
│   │   │
│   │   ├── agent/            # Orquestación y lógica central del LLM
│   │   │   ├── __init__.py
│   │   │   ├── engine.py     # Inicialización del modelo y bucle de ejecución
│   │   │   ├── prompts.py    # System prompts adaptados a actuación directa en VPS
│   │   │   └── context.py    # Inyección dinámica de cwd basada en parent_directory
│   │   │
│   │   ├── tools/            # Herramientas nativas de actuación en el host Linux
│   │   │   ├── __init__.py
│   │   │   ├── terminal.py   # Ejecución nativa de Bash (subprocess orientado al cwd)
│   │   │   ├── files.py      # Lectura, edición, creación y búsqueda de archivos
│   │   │   └── git.py        # Gestión nativa de repositorios e historial Git
│   │   │
│   │   ├── db/               # Capa de datos
│   │   │   ├── __init__.py
│   │   │   └── client.py     # Conexión al SDK de PocketBase (projects, sessions, messages)
│   │   │
│   │   └── api/
│   │       ├── __init__.py
│   │       ├── routes.py     # Gestión de rutas HTTP y streaming SSE
│   │       └── schemas.py    # Modelos Pydantic (AgentContext, Payloads de salida)
│   │
│   ├── pyproject.toml        # Definición del paquete Python para instalación global
│   ├── requirements.txt      # Dependencias del sistema (fastapi, uvicorn, pocketbase, etc.)
│   └── .env.example          # Plantilla de variables de configuración
│
├── deploy/                   # Instalación y configuración global en el VPS
│   ├── hermetic.service      # Plantilla oficial de systemd para Linux
│   ├── install.sh            # Script de aprovisionamiento global (compila e instala)
│   └── pb_schema.json        # Esquema actualizado con la colección 'projects'
│
├── .gitignore
└── README.md
