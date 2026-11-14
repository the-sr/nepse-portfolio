# NEPSE Portfolio Tracker

A full-stack portfolio tracker for the Nepal Stock Exchange. It tracks holdings at the latest
scraped price, computes FIFO P&L, suggests averaging-down tranches, scores buy/hold/sell signals
with an explainable rule engine (or a scikit-learn model), and streams price alerts over SSE.

> **Disclaimer: This application is not a financial adviser.** Prices can be delayed or incomplete, and the signals
> ignore your personal objectives and tax situation.

---

## Stack

| Layer      | Technology                                   |
| ---------- | -------------------------------------------- |
| Frontend   | React, TypeScript                            |
| Backend    | Java, Spring Boot                            |
| Database   | PostgreSQL                                   |
| ML service | Python, FastAPI, scikit-learn (RandomForest) |

---

## Features

- **Authentication** — register/login, BCrypt passwords, short-lived JWT access tokens, rotating
  refresh tokens stored in Postgres, automatic refresh on the frontend.
- **Market data** — the full security universe is imported from NEPSE on startup, then prices
  refresh every 90 s during NEPSE hours (Mon–Fri 11:00–15:00 NPT) from NEPSE's own live-market
  API, with the Sharesansar table scrape as an automatic fallback. Scrape-run audit log and
  provider health in the admin status endpoint.
- **Portfolio** — buy/sell/IPO/FPO/right/bonus/auction transactions with the full Nepali cost
  engine: tiered broker commission, SEBON fee, DP charge (once per scrip per day), FIFO lot
  matching for quantities, weighted-average cost for tax (Income Tax Act 2058 §95क(2)),
  realized and unrealized P&L, allocation weights, transaction history, and a live
  "capital gains tax if you sold everything today" report. Every rate is editable
  (trade override > user settings > admin global > verified defaults).
- **Charts** — portfolio value history, allocation donut, per-stock price chart with 1M/3M/6M/1Y
  ranges.
- **Averaging engine** — configurable −5% / −10% / −15% trigger levels, tranche budgets, position
  and per-stock capital caps, plus an interactive "by quantity / by budget / target average"
  simulator with feasibility checks.
- **Recommendations** — weighted technical score (trend 30, RSI 20, momentum 20, position 15,
  volume 10, concentration 5) with human-readable reasons, scored by the built-in rule engine.
- **Price alerts** — above, below, and daily percentage move alerts; evaluated on each scrape;
  one-shot with re-arm; live SSE feed and persisted event history.
- **Reports** — realized gains by FIFO lot, holding period, estimated capital gains tax at
  configurable short/long rates, CSV export.
- **Contact** — validated message stored in the database; optional SMTP email to the admin.
- **UX** — dark/light theme, responsive layout, animated page/card transitions, skeletons, toasts,
  accessible forms, live market ticker.

---

## Prerequisites

Choose one way to run the application:

- **Docker Compose:** Docker Engine and the Docker Compose v2 plugin.
- **Manual setup:** PostgreSQL 16, JDK 21+, Node.js 20+, and Python 3.12+.

Docker Compose runs PostgreSQL, the backend, frontend, and ML service together. For manual setup, install and run those dependencies yourself.

### Install Docker (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"   # log out/in or run: newgrp docker
docker version
```

If `docker info` reports _permission denied_ or _cannot connect to the Docker daemon_, the daemon
is not running — start it with `sudo systemctl enable --now docker`.

### Install Java, Node, PostgreSQL and Python (manual setup only)

```bash
# Node 20+ (nvm recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
nvm install 20

# JDK 21
sudo apt install -y openjdk-21-jdk
java -version

# PostgreSQL
sudo apt install -y postgresql

# Python runtime, venv and package manager
sudo apt install -y python3, python3-venv, python3-pip
```

---

## Quick start (Docker)

```bash
cd nepse-portfolio
cp .env.example .env
# Edit .env: set a long random JWT_SECRET, and SMTP values if you want contact emails.
openssl rand -base64 48        # paste output as JWT_SECRET
docker compose up --build -d
docker compose ps
```

| Service    | URL                                    |
| ---------- | -------------------------------------- |
| App        | http://localhost:3000                  |
| Swagger UI | http://localhost:8080/swagger-ui.html  |
| ML docs    | http://localhost:8000/docs             |
| Postgres   | `localhost:5432` (user/db from `.env`) |

First startup runs Flyway, imports NEPSE's live security universe, and backfills price history.
Give the backend ~20–40 s on the first run.

### Stop / reset

```bash
docker compose down          # keep data
docker compose down -v       # delete database and model volumes
```

---

## Manual setup (without Docker)

If you followed **Prerequisites → Install Java, Node, PostgreSQL and Python**, you already have
everything needed. Run each service in its own terminal. The backend defaults target
`localhost:5432` (Postgres), and the frontend proxies `/api` to `localhost:8080`.

### 1. PostgreSQL — the database

Create the database and user that the backend expects by default (skip if your Postgres already
has them):

```bash
sudo -u postgres psql <<'SQL'
CREATE USER nepse WITH PASSWORD 'nepse';
CREATE DATABASE nepse_portfolio OWNER nepse;
SQL
```

Confirm the server is up and reachable on port 5432:

```bash
sudo systemctl status postgresql
```

### 2. Backend — Java / Spring Boot

```bash
cd backend
./mvnw spring-boot:run
```

On startup it runs Flyway migrations, imports NEPSE's active security universe, and backfills price
history (first run takes ~20–40 s). Override the defaults by exporting any of `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, or the `SCRAPER_*` / `ML_*` variables before running.

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

### 3. Frontend — React / Vite

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

The Vite dev server already proxies `/api` to `http://localhost:8080`, so no extra config is
needed. For a production bundle: `npm run build` (output in `dist/`), then serve `dist/`.

### 4. ML service — Python (optional)

Only needed if you want ML-based recommendations. Without it (or without a trained model) the
backend uses its built-in rule engine, so you can skip this step entirely.

```bash
cd ml-service
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --port 8000
```

- Docs / health: `http://localhost:8000/docs`, `http://localhost:8000/health`
- Point the backend at it with `ML_SERVICE_URL=http://localhost:8000` (default) and `ML_ENABLED=true`.

### 5. Environment variables

Local runs read from `.env.example` (copy it to `.env`) and `backend/src/main/resources/application.yml`.
At minimum set a long random `JWT_SECRET` (`openssl rand -base64 48`), and adjust `DB_*` / `CORS_ORIGINS`
if your Postgres differs from the defaults above.

### Verify

Open `http://localhost:5173`, register a user, and add a trade. The first page load waits for the
backend's startup sync to finish.

---

## Tests

```bash
make backend-test    # JUnit: FIFO, indicators, DCA, scraper parsing
make frontend-test   # Vitest: formatting helpers
make ml-test         # pytest: feature/label engineering
```

Current baseline: backend 33 tests (FIFO, indicators, DCA, scraper parsing, NEPSE token/WASM,
security-universe and CSV import, plus one live NEPSE integration test), frontend 6 tests, ML
feature tests (3 tests). After the stack is running, also run `make smoke` (or
`bash tests/api-smoke.sh`) to verify the deployed API and reports end-to-end (register a user and
seed a trade first, since the script logs in with `NEPSE_EMAIL` / `NEPSE_PASSWORD`).

---

## API overview

All routes below are under `/api`; everything except auth, public market reads and contact requires
`Authorization: Bearer <accessToken>`.

| Area      | Endpoints                                                                                                                                                          |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Auth      | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`                                                               |
| Market    | `GET /market/stocks`, `GET /market/stocks/{symbol}?range=1M`, `GET /market/movers`, `GET /market/sectors`                                                          |
| Portfolio | `GET /portfolio`, `GET /portfolio/trades`, `POST /portfolio/trades`, `DELETE /portfolio/trades/{id}`, `GET /portfolio/history?days=365`                            |
| Analysis  | `GET /analysis/recommendations`, `GET /analysis/stocks/{symbol}`, `GET /analysis/opportunities`, `POST /analysis/average-simulation`                               |
| Alerts    | `GET/POST /alerts`, `PUT/DELETE /alerts/{id}`, `GET /alerts/events`                                                                                                |
| Reports   | `GET /reports/realized-gains?from=&to=&format=csv`                                                                                                                 |
| Settings  | `GET/PUT /settings`, `GET /settings/trading-rates`, `GET /settings/trading-rates/effective`, `GET /settings/trading-rates/defaults`, `PUT /settings/trading-rates` |
| Contact   | `POST /contact`                                                                                                                                                    |
| Streams   | `GET /market/stream`, `GET /alerts/stream?access_token=…` (SSE)                                                                                                    |
| Admin     | `GET /admin/market/status`, `POST /admin/market/sync`, `GET /admin/ml/status`                                                                                      |

Error responses use a consistent `{timestamp, status, error, message, path}` body. Validation errors
include a `fieldErrors` map.

---

## Configuration

Key variables (see `.env.example` and `backend/src/main/resources/application.yml`):

| Variable                                  | Default             | Purpose                                                  |
| ----------------------------------------- | ------------------- | -------------------------------------------------------- |
| `JWT_SECRET`                              | dev value           | HMAC key, **must** be replaced in production (32+ chars) |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | 30 / 14             | Token lifetimes                                          |
| `CORS_ORIGINS`                            | localhost:5173,3000 | Comma-separated allowed origins                          |
| `SCRAPER_ENABLED`                         | true                | Toggle market data refresh                               |
| `SCRAPER_INTERVAL_MS`                     | 90000               | Refresh cadence                                          |
| `SCRAPER_IGNORE_MARKET_HOURS`             | false               | Demo mode outside NEPSE hours                            |
| `MARKET_PROVIDERS`                        | `nepse,sharesansar` | Provider order; first success wins                       |
| `NEPSE_ENABLED`                           | true                | Enable NEPSE's live-market API                           |
| `HISTORY_BACKFILL_BASE_URL`               | socrateai dataset   | Source for real historical CSVs                          |
| `MAIL_*`, `ADMIN_EMAIL`, `MAIL_FROM`      | blank               | Optional contact-form email                              |

---

## Project layout

```
nepse-portfolio/
├── docker-compose.yml
├── Makefile
├── .env.example
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/nepse/portfolio/
│       ├── auth/ market/ portfolio/ analysis/ recommend/
│       ├── alert/ contact/ report/ stream/ user/
│       ├── common/ config/ security/ seed/
│       └── ...
│   └── src/main/resources/db/migration/   # Flyway V1 schema
├── frontend/
│   ├── Dockerfile, nginx.conf
│   └── src/
│       ├── pages/ components/ charts/ context/ lib/ types/
├── ml-service/
│   ├── Dockerfile
│   └── app/  # FastAPI app, features, model, db
└── tests/
```

---

## Troubleshooting

**`Cannot connect to the Docker daemon`** — start Docker: `sudo systemctl enable --now docker`,
then re-login (or `newgrp docker`) so group membership applies.

**Port already in use** — change `FRONTEND_PORT`, `BACKEND_PORT`, `ML_PORT` or `POSTGRES_PORT` in
`.env`.

**Backend restarts or fails on startup** — check `docker compose logs backend`. Typical causes are a
short `JWT_SECRET`, Postgres not healthy yet, or a Flyway checksum mismatch after editing an applied
migration (reset the dev volume with `docker compose down -v`).

**Prices do not update** — NEPSE may be closed, Sharesansar markup may have changed, or outbound
network access may be blocked. Check `docker compose logs backend` for scrape errors and
`/api/admin/market/status`. The last known prices remain usable meanwhile.

**SSE disconnects behind a proxy** — keep `proxy_buffering off` and a long `proxy_read_timeout`
(already configured in `frontend/nginx.conf`). The alerts stream authenticates with
`?access_token=` because native EventSource cannot set headers.

---

## Security and disclaimer

- The SPA stores the access and refresh tokens in `localStorage` for simplicity and refreshes the
  access token automatically; refresh tokens are also persisted server-side and rotated on use. For
  a hardened deployment, move tokens to httpOnly cookies and add a strict Content-Security-Policy.
- Passwords are BCrypt-hashed. Never commit `.env`.
- Run behind TLS, restrict CORS, and rotate `JWT_SECRET`.
- This project is for education and personal record-keeping. It is **not** a brokerage, does not
  place orders, and does not provide financial, tax or investment advice. Verify every number
  against your broker and consult a licensed professional before acting.
