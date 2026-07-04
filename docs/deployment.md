# Despliegue

## VPS

- **IP**: `144.217.161.133`
- **Usuario**: `debian`
- **Base path**: `/home/debian/dev/`
- **Red**: Tailscale mesh VPN (sin exposición pública)

## Backend

```bash
cd /home/debian/dev/hermetic
python -m venv .venv
source .venv/bin/activate
pip install -e .
hermetic  # uvicorn en 0.0.0.0:8000
```

Variables de entorno en `backend/.env`:
- `POCKETBASE_URL`
- `POCKETBASE_ADMIN_EMAIL`
- `POCKETBASE_ADMIN_PASSWORD`
- `HOST` / `PORT`

## PocketBase

Instancia en `https://db-2.jville.dev`. Schema en `deploy/pb_schema.json`.

## Seguridad

- Sin JWT — solo accesible vía Tailscale
- Tráfico en texto claro dentro de la mesh
- Credenciales de PocketBase con acceso de admin
