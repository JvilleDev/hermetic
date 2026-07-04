from pathlib import Path


def read_file(path: str) -> str:
    try:
        return Path(path).read_text()
    except Exception as e:
        return f"Error reading file: {e}"


def write_file(path: str, content: str) -> str:
    try:
        p = Path(path)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)
        return f"File written: {path}"
    except Exception as e:
        return f"Error writing file: {e}"


def list_directory(path: str) -> str:
    try:
        entries = list(Path(path).iterdir())
        if not entries:
            return "(empty directory)"
        lines = []
        for e in sorted(entries):
            suffix = "/" if e.is_dir() else ""
            lines.append(f"{e.name}{suffix}")
        return "\n".join(lines)
    except Exception as e:
        return f"Error listing directory: {e}"
