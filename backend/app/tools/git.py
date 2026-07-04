from .terminal import run_command


def git_status(cwd: str | None = None) -> str:
    return run_command("git status", cwd=cwd)


def git_log(cwd: str | None = None, count: int = 10) -> str:
    return run_command(f"git log --oneline -{count}", cwd=cwd)


def git_diff(cwd: str | None = None) -> str:
    return run_command("git diff", cwd=cwd)
