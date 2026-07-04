from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    pocketbase_url: str = "http://127.0.0.1:8090"
    pocketbase_admin_email: str = "admin@example.com"
    pocketbase_admin_password: str = "changeme"

    host: str = "0.0.0.0"
    port: int = 8000

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
