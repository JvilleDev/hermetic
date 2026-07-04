# Android — Estructura del Proyecto

App nativa Android en Kotlin + Jetpack Compose + Hilt.

## Organización

```
android/app/src/main/java/com/hermetic/app/
├── HermeticApp.kt          # @HiltAndroidApp
├── MainActivity.kt         # Single Activity + Splash + Edge-to-Edge
├── data/
│   ├── model/Models.kt     # Data classes: Project, Session, Message, Provider, MCPServer, Skill, SSEEvent...
│   ├── remote/HermeticApi.kt  # OkHttp client: health, SSE streaming, REST CRUD
│   └── repository/ChatRepository.kt  # Flow<SSEEvent> wrapper
├── di/
│   ├── HiltApiEntryPoint.kt
│   └── NetworkModule.kt    # Gson singleton
└── ui/
    ├── chat/
    │   ├── ChatScreen.kt       # UI completa del chat
    │   └── ChatViewModel.kt    # Estado + lógica SSE
    ├── components/
    │   └── DirectoryTreeBrowser.kt
    ├── explorer/ExplorerScreen.kt
    ├── navigation/NavGraph.kt
    ├── projects/ProjectsScreen.kt
    ├── providers/ProvidersScreen.kt
    ├── sessions/SessionHistoryScreen.kt
    ├── settings/SettingsScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## Pantallas

- **Chat** — Logo hexágono, panel de thinking, tool calls, burbujas de mensajes, input + send/stop
- **Historial** — Lista de sesiones con búsqueda
- **Proyectos** — Tarjetas con icono de carpeta, estado, opciones
- **Explorador** — Vista de árbol/plana del sistema de archivos
- **Providers** — Lista con badges de logo (G/O/A) y estado
- **MCP Servers** — Gestión de servidores MCP
- **Skills** — Lista de skills activos
- **Settings** — Conexión al servidor, URL configurable

## Dependencias principales

- Jetpack Compose + Material 3
- Hilt (DI)
- OkHttp + OkHttp-SSE
- Navigation Compose
- Lifecycle ViewModel Compose
