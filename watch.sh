#!/bin/bash

APP_PACKAGE="com.hermetic.app"
ANDROID_DIR="$(cd "$(dirname "$0")/android" && pwd)"
WATCH_DIRS=(
  "$ANDROID_DIR/app/src"
  "$ANDROID_DIR/build.gradle.kts"
  "$ANDROID_DIR/settings.gradle.kts"
  "$ANDROID_DIR/gradle.properties"
)

REF_FILE=$(mktemp /tmp/hermetic-watch.XXXXXX)
trap "rm -f $REF_FILE; exit" EXIT
trap "exit" SIGINT SIGTERM

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $1"; }
warn()  { echo -e "${YELLOW}[$(date '+%H:%M:%S')]${NC} $1"; }
error() { echo -e "${RED}[$(date '+%H:%M:%S')]${NC} $1" >&2; }
logcat_msg() { echo -e "${BLUE}[logcat]${NC} $1"; }

# Logcat noise filter — tags del framework Android que saturan el output
NOISE_TAGS="Zygote|nativeloader|GraphicsEnvironment|DisplayManager|vulkan|AdrenoVK|AdrenoUtils|Adreno-AppProfiles|HWUI|HardwareRenderer|BufferQueueProducer|BLASTBufferQueue|SurfaceComposerClient|Choreographer|InsetsController|InsetsSourceConsumer|InputTransport|InputMethodManager|ImeTracker|DecorView|VRI\[|ActivityThread|CompatChangeReporter|DesktopModeFlags|DesktopExperienceFlags|WindowOnBackDispatcher|NativeCustomFrequencyManager|SurfaceFlinger|ashmem|libc|qdgralloc|BLASTBufferQueue_Java|IDS_TAG|om\.hermetic\.app"

cd "$ANDROID_DIR"

info "Hermetic Watch iniciado"
info "Ruta: $ANDROID_DIR"
echo ""

APP_ACTIVITY=".MainActivity"

launch_app() {
  adb shell am start -n "$APP_PACKAGE/$APP_ACTIVITY" > /dev/null 2>&1
}

info "Compilación inicial..."
if ./gradlew installDebug 2>&1; then
  info "APK instalado correctamente"
  launch_app
else
  error "Error al compilar"
fi
touch "$REF_FILE"
echo ""

LOGCAT_PID=""
follow_logcat() {
  local last_pid=""
  while true; do
    local pid=$(adb shell pidof "$APP_PACKAGE" 2>/dev/null | awk '{print $NF}')
    if [ -z "$pid" ] && [ -n "$last_pid" ]; then
      logcat_msg "App cerrada, esperando reinicio..."
      last_pid=""
    elif [ -n "$pid" ] && [ "$pid" != "$last_pid" ]; then
      [ -n "$LOGCAT_PID" ] && kill "$LOGCAT_PID" 2>/dev/null
      sleep 0.5
      last_pid="$pid"
      logcat_msg "App PID $pid — mostrando logs:"
      adb logcat --pid="$pid" -v brief \
        | grep -v -E "^(I|D|V|W)/($NOISE_TAGS)" \
        | grep -v "^$" \
        | while IFS= read -r line; do
            case "$line" in
              E/*) echo -e "${RED}$line${NC}" ;;
              W/*) echo -e "${YELLOW}$line${NC}" ;;
              I/*) echo -e "${CYAN}$line${NC}" ;;
              D/*) echo -e "${NC}$line" ;;
              *)   echo "$line" ;;
            esac
          done &
      LOGCAT_PID=$!
    fi
    sleep 3
  done
}

follow_logcat &
FOLLOW_PID=$!
trap "kill $FOLLOW_PID 2>/dev/null; [ -n \"$LOGCAT_PID\" ] && kill $LOGCAT_PID 2>/dev/null; rm -f $REF_FILE; exit" SIGINT SIGTERM EXIT

while true; do
  CHANGED=""
  for dir in "${WATCH_DIRS[@]}"; do
    if [ -f "$dir" ]; then
      if [ "$dir" -nt "$REF_FILE" ]; then
        CHANGED="$dir"
        break
      fi
    elif [ -d "$dir" ]; then
      c=$(find "$dir" -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.xml" -o -name "*.properties" \) -newer "$REF_FILE" 2>/dev/null | head -1)
      if [ -n "$c" ]; then
        CHANGED="$c"
        break
      fi
    fi
  done

  if [ -n "$CHANGED" ]; then
    info "Cambio detectado, esperando 5s por si hay más..."
    sleep 5
    touch "$REF_FILE"
    info "Compilando e instalando..."

    if ./gradlew installDebug 2>&1; then
      info "APK instalado correctamente"
      launch_app
    else
      error "Error al compilar"
    fi
    echo ""
  fi

  sleep 2
done
