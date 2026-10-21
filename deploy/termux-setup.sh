#!/data/data/com.termux/files/usr/bin/sh
#
# termux-setup.sh — one-shot installer that turns a Termux phone into the
# NEPSE portfolio server (PostgreSQL + Spring Boot JAR + nginx + auto-deploy).
#
# Run from a clone of the repository inside Termux:
#   pkg install -y git && git clone https://github.com/the-sr/nepse-portfolio.git
#   cd nepse-portfolio && bash deploy/termux-setup.sh
#
# Idempotent: safe to run again. It never touches your Docker stack on the PC.
# The ML service is intentionally not installed (sklearn cannot be pip-installed
# on Android); the backend falls back to its rule engine automatically.

set -eu

NEPSE_HOME="$HOME/nepse"
PGDATA="$HOME/pgdata"
APP_PORT=3000          # nginx (frontend + API proxy) - what visitors open
BACKEND_PORT=8080
REPO="${GITHUB_REPO:-the-sr/nepse-portfolio}"
BRANCH="${GITHUB_BRANCH:-main}"

log() { printf '\n\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }

[ -d /data/data/com.termux ] || { warn "Run this inside Termux"; exit 1; }

log "Installing packages (openjdk-21, postgresql, nginx, openssh, curl, unzip, git)"
pkg update -y >/dev/null 2>&1 || true
pkg install -y openjdk-21 postgresql nginx openssh curl unzip git ncurses-utils >/dev/null

# ---------------------------------------------------------------- postgres ---
log "Preparing PostgreSQL (data dir: $PGDATA)"
if [ ! -f "$PGDATA/PG_VERSION" ]; then
    initdb -D "$PGDATA" -E UTF8 >/dev/null
fi
if ! psql -d postgres -c 'select 1' >/dev/null 2>&1; then
    pg_ctl -D "$PGDATA" -l "$PGDATA/postgres.log" start >/dev/null
fi
psql -d postgres -c 'select 1' >/dev/null

DB_NAME=nepse_portfolio
DB_USER="$(whoami)"
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1 \
    || createdb "$DB_NAME"

# ------------------------------------------------------------------- .env ----
log "Writing $NEPSE_HOME/.env"
mkdir -p "$NEPSE_HOME/scripts"
if [ -f "$NEPSE_HOME/.env" ]; then
    warn ".env already exists - keeping your JWT secret and settings"
else
    JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '/+=' )"
    cat > "$NEPSE_HOME/.env" <<EOF
# NEPSE portfolio - Termux runtime configuration
export NEPSE_HOME="$NEPSE_HOME"
export APP_PORT=$APP_PORT
export BACKEND_PORT=$BACKEND_PORT
export GITHUB_REPO="$REPO"

export SERVER_PORT=$BACKEND_PORT
export DB_URL="jdbc:postgresql://127.0.0.1:5432/$DB_NAME"
export DB_USER="$DB_USER"
export DB_PASSWORD=""

# Keep this secret. Re-generate: head -c 48 /dev/urandom | base64
export JWT_SECRET="$JWT_SECRET"

# ML service is not installed on the phone; the Java rule engine takes over.
export ML_ENABLED=false

export NEPSE_ENABLED=true
export NEPSE_UNIVERSE_ON_STARTUP=true
export SCRAPER_ENABLED=true
export HISTORY_BACKFILL_AUTO=true
export HISTORY_BACKFILL_DAYS=260

# Keep the heap small; phones kill memory hogs fast.
export JAVA_OPTS="-Xms128m -Xmx640m -XX:+ExitOnOutOfMemoryError"
EOF
    chmod 600 "$NEPSE_HOME/.env"
fi

# ------------------------------------------------------------- app scripts ---
log "Writing start/stop scripts into $NEPSE_HOME/scripts"
cat > "$NEPSE_HOME/scripts/start-postgres.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/sh
if ! psql -d postgres -c 'select 1' >/dev/null 2>&1; then
    pg_ctl -D "$PGDATA" -l "$PGDATA/postgres.log" start
fi
EOF
# Quoted heredocs below: nothing is interpolated at generation time, the
# scripts resolve everything at run time.
cat > "$NEPSE_HOME/scripts/stop-backend.sh" <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
PIDFILE="$HOME/nepse/backend.pid"
PID=""
if [ -f "$PIDFILE" ]; then
    PID="$(cat "$PIDFILE" 2>/dev/null)"
fi
if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    i=0
    while [ "$i" -lt 12 ]; do
        kill -0 "$PID" 2>/dev/null || break
        sleep 0.5
        i=$((i + 1))
    done
    kill -9 "$PID" 2>/dev/null || true
fi
rm -f "$PIDFILE"
# Belt & braces: also catch instances the pidfile missed (stale pid, races)
pkill -f '[n]epse-portfolio.jar' 2>/dev/null || true
echo "backend stopped"
SH
cat > "$NEPSE_HOME/scripts/start-backend.sh" <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
. "$HOME/nepse/.env"
cd "$NEPSE_HOME" || cd /data/data/com.termux/files/home/nepse || exit 1
nohup java $JAVA_OPTS -jar "$NEPSE_HOME/nepse-portfolio.jar" >> "$NEPSE_HOME/backend.log" 2>&1 &
echo $! > "$NEPSE_HOME/backend.pid"
echo "backend starting (pid $(cat "$NEPSE_HOME/backend.pid"))"
SH
cat > "$NEPSE_HOME/scripts/start-web.sh" <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
nginx -s quit 2>/dev/null || true
sleep 1
nginx -t 2>/dev/null || echo "nginx config test failed - check $HOME/nepse/nginx-error.log"
nginx
SH
chmod +x "$NEPSE_HOME"/scripts/*.sh

# ------------------------------------------------------------------ nginx ----
log "Configuring nginx: web + API proxy on port $APP_PORT"
NGINX_ROOT="$(command -v nginx >/dev/null && dirname "$(dirname "$(command -v nginx)")")"
NGINX_PREFIX="${NGINX_ROOT:-/data/data/com.termux/files/usr}"
cat > "$PREFIX/etc/nginx/nginx.conf" <<EOF
worker_processes 1;
error_log $NEPSE_HOME/nginx-error.log;
pid $PREFIX/var/run/nginx-nepse.pid;
# NOTE: no "daemon off;" here — with it, `nginx` blocks the calling script
# forever (that was the hang you hit on setup). Default daemonize-on-start.

events { worker_connections 512; }

http {
    include $NGINX_PREFIX/etc/nginx/mime.types;
    default_type application/octet-stream;
    access_log off;
    sendfile on;
    client_max_body_size 10m;

    server {
        listen $APP_PORT;

        # API (and SSE streams) proxy to the Spring Boot backend
        location /api/ {
            proxy_pass http://127.0.0.1:$BACKEND_PORT;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header Connection "";
            proxy_buffering off;          # required for SSE streams
            proxy_read_timeout 3600s;     # long-lived alerts/price streams
        }
        location = /actuator/health  { proxy_pass http://127.0.0.1:$BACKEND_PORT; }
        location /v3/api-docs        { proxy_pass http://127.0.0.1:$BACKEND_PORT; }
        location /swagger-ui         { proxy_pass http://127.0.0.1:$BACKEND_PORT; }

        # Built frontend with SPA history fallback
        root "$NEPSE_HOME/web";
        location / {
            try_files \$uri /index.html;
        }
        location /assets/ {
            expires 30d;
            add_header Cache-Control "public";
        }
    }
}
EOF

# ------------------------------------------------- first bootstrap via git ---
if [ ! -f "$NEPSE_HOME/nepse-portfolio.jar" ]; then
    log "Fetching the latest application from GitHub releases"
    TAG="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
        | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' || true)"
    if [ -n "${TAG:-}" ]; then
        curl -fsSL -o "$NEPSE_HOME/nepse-portfolio.jar" \
            "https://github.com/$REPO/releases/download/$TAG/nepse-portfolio.jar"
        curl -fsSL -o "$NEPSE_HOME/frontend-dist.zip" \
            "https://github.com/$REPO/releases/download/$TAG/frontend-dist.zip"
        unzip -oq "$NEPSE_HOME/frontend-dist.zip" -d "$NEPSE_HOME/web"
        echo "$TAG" > "$NEPSE_HOME/VERSION"
    else
        warn "No GitHub release found yet - see docs/deploy-android.md section 'First deploy'"
    fi
fi

# -------------------------------------------------------------- auto deploy --
log "Scheduling hourly auto-deploy checks"
cat > "$NEPSE_HOME/scripts/autoupdate.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/sh
( while true; do
    sleep 3600
    bash "$NEPSE_HOME/deploy.sh" >> "$NEPSE_HOME/deploy.log" 2>&1 || true
done ) >/dev/null 2>&1 &
EOF
chmod +x "$NEPSE_HOME/scripts/autoupdate.sh"
# Copy the deploy script next to the app dir so the hourly loop uses the
# freshest version even if this repo clone later disappears.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/termux-deploy.sh" ] && [ "$SCRIPT_DIR" != "$NEPSE_HOME" ]; then
    cp -f "$SCRIPT_DIR/termux-deploy.sh" "$NEPSE_HOME/deploy.sh"
fi
chmod +x "$NEPSE_HOME/deploy.sh" 2>/dev/null || true

# --------------------------------------------------------- boot autostart ----
log "Registering boot autostart (requires the Termux:Boot app)"
BOOT_DIR="$HOME/.termux/boot"
mkdir -p "$BOOT_DIR"
cat > "$BOOT_DIR/01-nepse.sh" <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
sleep 20
# clean slate: nothing may survive from a previous session
pg_ctl -D "$HOME/pgdata" -l "$HOME/pgdata/postgres.log" start 2>/dev/null || true
sshd 2>/dev/null || true
bash "$HOME/nepse/scripts/start-postgres.sh"
nginx -s quit 2>/dev/null || true
sleep 1
pkill -f '[n]epse-portfolio.jar' 2>/dev/null || true
rm -f "$HOME/nepse/backend.pid"
bash "$HOME/nepse/scripts/start-web.sh"
bash "$HOME/nepse/scripts/start-backend.sh"
bash "$HOME/nepse/scripts/autoupdate.sh"
sleep 90
bash "$HOME/nepse/deploy.sh" >> "$HOME/nepse/deploy.log" 2>&1
SH
chmod +x "$BOOT_DIR/01-nepse.sh"

termux-wake-lock
# make the phone reachable from a PC right now (docs: `make phone` / ssh -p 8022)
command -v sshd >/dev/null || pkg install -y openssh >/dev/null
(sshd 2>/dev/null || true)

# ------------------------------------------------------------------ start ----
log "Starting services for the first time"
bash "$NEPSE_HOME/scripts/start-web.sh"
if [ -f "$NEPSE_HOME/nepse-portfolio.jar" ]; then
    bash "$NEPSE_HOME/scripts/start-backend.sh"

    log "Waiting for first boot (universe import + history backfill take a while)"
    ok=""
    for _ in $(seq 1 60); do
        sleep 5
        if curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health" 2>/dev/null | grep -q UP; then
            ok=1; break
        fi
    done
    if [ -n "$ok" ]; then
        echo "Backend is UP:      http://127.0.0.1:$BACKEND_PORT/actuator/health"
    else
        warn "Backend did not report UP within 5 minutes - check $NEPSE_HOME/backend.log"
    fi
fi

cat <<'EOF'

Setup complete. Next steps:
  1. Control the phone from your PC (keyboard comfort): ssh -p 8022 <whoami>@<phone-ip>
     (make phone on your PC does the adb-forward variant; a password was never
     part of setup, the one you typed with `passwd` is the SSH password)
  2. App URL (on the phone / LAN):   http://<phone-ip>:3000
  3. Public URL for friends:         cloudflared tunnel --url http://localhost:3000
     (see docs/deploy-android.md for a stable ngrok static domain option)
  4. Updates happen automatically every hour.
     Manual redeploy any time:      bash ~/nepse/deploy.sh
  5. Recommended Android hardening:  see docs/deploy-android.md
     (battery unrestricted, phantom-process fix, Termux:Boot)
Reminder: recommendations in the app are educational, not financial advice.
EOF
