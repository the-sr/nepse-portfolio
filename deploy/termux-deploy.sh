#!/data/data/com.termux/files/usr/bin/sh
#
# termux-deploy.sh — pull-based deployment for the Android/Termux host.
#
# Checks the latest GitHub Release of the application and, when there is a new
# version, downloads the fat JAR + frontend dist, swaps them in and restarts
# the backend. Designed to be run by the hourly autoupdate loop (or Termux:Boot
# at startup). It never builds anything locally.
#
# Usage:
#   bash termux-deploy.sh              # update if a newer release exists
#   bash termux-deploy.sh --force      # redeploy even if the version matches
#   bash termux-deploy.sh --version v0.1.0   # deploy a specific release tag
#   bash termux-deploy.sh --check      # only report, do not change anything
#
# Rollback: the previous JAR is kept as nepse-portfolio.jar.prev —
#   mv ~/nepse/nepse-portfolio.jar.prev ~/nepse/nepse-portfolio.jar
#   bash ~/nepse/scripts/stop-backend.sh && bash ~/nepse/scripts/start-backend.sh

set -u

NEPSE_HOME="${NEPSE_HOME:-$HOME/nepse}"
REPO="${GITHUB_REPO:-the-sr/nepse-portfolio}"

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

MODE=apply
WANT_TAG=""
FORCE=0
for arg in "$@"; do
    case "$arg" in
        --force) FORCE=1 ;;
        --check) MODE=check ;;
        --version) WANT_TAG="$2"; shift ;;
        -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
        *) log "unknown argument: $arg"; exit 1 ;;
    esac
done

[ -d "$NEPSE_HOME" ] || { log "error: $NEPSE_HOME not found — run deploy/termux-setup.sh first"; exit 1; }
[ -f "$NEPSE_HOME/.env" ] && . "$NEPSE_HOME/.env"

# ---------------------------------------------------------------- which tag ---
if [ -n "$WANT_TAG" ]; then
    TAG="$WANT_TAG"
else
    RELEASE_JSON="$(curl -fsSL --max-time 30 "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null)" \
        || { log "GitHub API unreachable — will retry next cycle"; exit 0; }
    TAG="$(printf '%s' "$RELEASE_JSON" | sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p')"
    if [ -z "$TAG" ]; then
        log "no published release found for $REPO yet — nothing to do"
        exit 0
    fi
fi

CURRENT="$(cat "$NEPSE_HOME/VERSION" 2>/dev/null || echo "")"
if [ "$TAG" = "$CURRENT" ] && [ "$FORCE" -eq 0 ]; then
    log "already up to date ($TAG)"
    exit 0
fi

log "new release available: $CURRENT -> $TAG"

if [ "$MODE" = check ]; then
    log "(check mode — nothing changed)"
    exit 0
fi

BASE="https://github.com/$REPO/releases/download/$TAG"
STAGING="$NEPSE_HOME/.staging.$TAG"
mkdir -p "$STAGING"
trap 'rm -rf "$STAGING"' EXIT

# ---------------------------------------------------------------- download ----
log "downloading $TAG assets"
curl -fsSL --max-time 300 -o "$STAGING/nepse-portfolio.jar" "$BASE/nepse-portfolio.jar" \
    || { log "download failed (jar) — keeping version $CURRENT"; exit 1; }
curl -fsSL --max-time 300 -o "$STAGING/frontend-dist.zip" "$BASE/frontend-dist.zip" \
    || { log "download failed (dist) — keeping version $CURRENT"; exit 1; }
unzip -oq "$STAGING/frontend-dist.zip" -d "$STAGING/web" \
    || { log "unzip failed — keeping version $CURRENT"; exit 1; }
[ -f "$STAGING/web/index.html" ] || { log "web bundle incomplete — keeping version $CURRENT"; exit 1; }

# ----------------------------------------------------------------- restart ----
log "stopping backend"
if [ -f "$NEPSE_HOME/scripts/stop-backend.sh" ]; then
    bash "$NEPSE_HOME/scripts/stop-backend.sh"
else
    if [ -f "$NEPSE_HOME/backend.pid" ]; then
        OLD="$(cat "$NEPSE_HOME/backend.pid" 2>/dev/null)"
        [ -n "$OLD" ] && kill "$OLD" 2>/dev/null || true
    fi
fi
# Always fall back to a jar-name kill: stale pidfiles, pid reuse or a missing
# stop script must never leave two instances racing for the port.
pkill -f '[n]epse-portfolio.jar' 2>/dev/null || true
for _ in $(seq 1 10); do
    pgrep -f '[n]epse-portfolio.jar' >/dev/null 2>&1 || break
    sleep 0.5
done
pkill -9 -f '[n]epse-portfolio.jar' 2>/dev/null || true
rm -f "$NEPSE_HOME/backend.pid"

cp -f "$NEPSE_HOME/nepse-portfolio.jar" "$NEPSE_HOME/nepse-portfolio.jar.prev" 2>/dev/null || true
mv -f "$STAGING/nepse-portfolio.jar" "$NEPSE_HOME/nepse-portfolio.jar"
rm -rf "$NEPSE_HOME/web.old"
[ -d "$NEPSE_HOME/web" ] && mv "$NEPSE_HOME/web" "$NEPSE_HOME/web.old"
mv "$STAGING/web" "$NEPSE_HOME/web"

log "starting backend"
bash "$NEPSE_HOME/scripts/start-backend.sh"

# frontend is served live from the directory — nginx needs a reload so it
# doesn't hold the old dir open
nginx -s reload 2>/dev/null || true

# --------------------------------------------------------------- healthcheck --
UP=0
for _ in $(seq 1 40); do
    if curl -fsS "http://127.0.0.1:${BACKEND_PORT:-8080}/actuator/health" 2>/dev/null | grep -q '"UP"'; then
        UP=1; break
    fi
    sleep 5
done

if [ "$UP" -eq 1 ]; then
    echo "$TAG" > "$NEPSE_HOME/VERSION"
    log "deployed $TAG successfully"
else
    log "WARNING: backend not UP within ~3 minutes after deploying $TAG"
    log "check $NEPSE_HOME/backend.log — rollback: keep the .jar.prev file as above"
    exit 1
fi
