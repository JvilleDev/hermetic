import logging
from app.config import settings

logger = logging.getLogger("notifier")

try:
    import firebase_admin
    from firebase_admin import credentials, messaging

    _FIREBASE_AVAILABLE = False

    def _init_firebase():
        global _FIREBASE_AVAILABLE
        try:
            if not firebase_admin._apps:
                cred = credentials.ApplicationDefault()
                firebase_admin.initialize_app(cred)
            _FIREBASE_AVAILABLE = True
        except Exception as e:
            logger.warning(f"Firebase not configured: {e}")
            _FIREBASE_AVAILABLE = False

except ImportError:
    firebase_admin = None
    messaging = None
    logger.warning("firebase-admin not installed, notifications disabled")

    def _init_firebase():
        pass

    _FIREBASE_AVAILABLE = False


async def send_push_to_all(title: str, body: str, data: dict | None = None) -> int:
    if not _FIREBASE_AVAILABLE:
        _init_firebase()
    if not _FIREBASE_AVAILABLE:
        logger.info(f"Notification skipped (Firebase not configured): {title} - {body}")
        return 0

    try:
        from app.db.client import _get_client
        client = await _get_client()
        r = await client.get("/api/collections/fcm_tokens/records", params={"perPage": 1000})
        r.raise_for_status()
        tokens = [item["token"] for item in r.json().get("items", []) if item.get("token")]
    except Exception as e:
        logger.error(f"Failed to fetch FCM tokens: {e}")
        return 0

    if not tokens:
        logger.info("No FCM tokens registered")
        return 0

    message = messaging.MulticastMessage(
        notification=messaging.Notification(title=title, body=body),
        data=data or {},
        tokens=tokens,
    )

    try:
        response = messaging.send_each_for_multicast(message)
        success = response.success_count
        logger.info(f"FCM: {success}/{len(tokens)} sent ({title})")
        return success
    except Exception as e:
        logger.error(f"FCM send failed: {e}")
        return 0


async def send_release_notification(version: str, release_url: str):
    await send_push_to_all(
        title="Nueva versión disponible",
        body=f"Hermetic v{version} ya está disponible",
        data={"version": version, "url": release_url, "type": "release"},
    )