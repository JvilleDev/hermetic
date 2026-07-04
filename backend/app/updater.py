import os
import shutil
import sys
import tarfile
import tempfile
import urllib.request
import json
import logging
import time

logger = logging.getLogger("updater")
logging.basicConfig(level=logging.INFO)

GITHUB_REPO = "JvilleDev/hermetic"
CURRENT_VERSION = "0.1.6"
UPDATE_INTERVAL_SECONDS = 6 * 60 * 60


def get_latest_release():
    url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
    try:
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Hermetic-Backend-Updater"}
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                data = json.loads(response.read().decode())
                return data
    except urllib.error.HTTPError as he:
        if he.code == 404:
            logger.info("No releases found on GitHub yet (HTTP 404).")
        else:
            logger.error(f"HTTP Error fetching latest release: {he.code}")
    except Exception as e:
        logger.error(f"Error fetching latest release: {e}")
    return None


def is_newer_version(current: str, latest: str) -> bool:
    try:
        c_parts = [int(x) for x in current.lstrip('v').split('.') if x.isdigit()]
        l_parts = [int(x) for x in latest.lstrip('v').split('.') if x.isdigit()]
        min_len = min(len(c_parts), len(l_parts))
        for i in range(min_len):
            if l_parts[i] > c_parts[i]:
                return True
            if l_parts[i] < c_parts[i]:
                return False
        return len(l_parts) > len(c_parts)
    except Exception:
        return False


def check_and_apply_update():
    logger.info("Checking for backend updates...")
    release = get_latest_release()
    if not release:
        return False

    latest_version = release.get("tag_name", "0.0.0")
    if not is_newer_version(CURRENT_VERSION, latest_version):
        logger.info(f"Backend is up to date (current: {CURRENT_VERSION}, latest: {latest_version})")
        return False

    logger.info(f"New backend version available: {latest_version}. Downloading update...")

    assets = release.get("assets", [])
    tar_asset = None
    for asset in assets:
        if asset.get("name") == "hermetic-backend.tar.gz":
            tar_asset = asset
            break

    if not tar_asset:
        logger.error("No hermetic-backend.tar.gz asset found in the latest release")
        return False

    download_url = tar_asset.get("browser_download_url")
    try:
        with tempfile.TemporaryDirectory() as tmpdir:
            tar_path = os.path.join(tmpdir, "update.tar.gz")
            req = urllib.request.Request(
                download_url,
                headers={"User-Agent": "Hermetic-Backend-Updater"}
            )
            with urllib.request.urlopen(req, timeout=30) as response, open(tar_path, 'wb') as out_file:
                out_file.write(response.read())

            with tarfile.open(tar_path, "r:gz") as tar:
                tar.extractall(path=tmpdir)

            src_dir = tmpdir
            dest_dir = os.getcwd()

            logger.info(f"Extracting updates from {src_dir} to {dest_dir}...")

            for root, dirs, files in os.walk(src_dir):
                dirs[:] = [d for d in dirs if d not in (".venv", "__pycache__", ".git")]

                relative_path = os.path.relpath(root, src_dir)
                dest_root = dest_dir if relative_path == "." else os.path.join(dest_dir, relative_path)

                os.makedirs(dest_root, exist_ok=True)

                for file in files:
                    if file in (".env", "local.properties"):
                        continue
                    src_file = os.path.join(root, file)
                    dest_file = os.path.join(dest_root, file)
                    shutil.copy2(src_file, dest_file)

            logger.info("Backend files successfully updated. Restarting application...")
            sys.exit(0)

    except Exception as e:
        logger.error(f"Error applying backend update: {e}")
        return False


def start_periodic_updates():
    """Background loop to check for updates periodically."""
    logger.info("Starting periodic update checker...")
    while True:
        try:
            check_and_apply_update()
        except Exception as e:
            logger.error(f"Unexpected error in periodic update loop: {e}")

        logger.info(f"Next update check in {UPDATE_INTERVAL_SECONDS // 3600} hours.")
        time.sleep(UPDATE_INTERVAL_SECONDS)
