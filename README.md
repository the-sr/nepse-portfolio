# NEPSE Portfolio Tracker

A full-stack portfolio tracker for the Nepal Stock Exchange. It tracks holdings at the latest
scraped price, computes FIFO P&L, suggests averaging-down tranches, scores buy/hold/sell signals
with an explainable rule engine (or a scikit-learn model), and streams price alerts over SSE.

> **Educational tool, not financial advice.** Prices can be delayed or incomplete, seeded history
> is clearly labelled, and the signals ignore your personal objectives and tax situation.

---

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query, Recharts, Framer Motion |
| Backend | Java 21, Spring Boot 3.5, Spring Security 6 + JWT, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| ML service | Python 3.12, FastAPI, scikit-learn (RandomForest) |
| Runtime | Docker Compose (4 services), or local dev servers |

The backend is a **modular monolith**: one deployable, internally split into `auth`, `market`,
`portfolio`, `analysis`, `recommend`, `alert`, `contact`, `report`, `settings`, `stream`,
`security`, `common`, `seed` and `config` packages. The ML model is the only separate service, so
it can be retrained without redeploying the API.

```
Browser ──► nginx (React SPA) ──/api──► Spring Boot ──► PostgreSQL
                                            │                ▲
                                            └──HTTP──► FastAPI ML ┘
                                            └──Jsoup──► Sharesansar live trading
```

---

## Features

- **Authentication** — register/login, BCrypt passwords, short-lived JWT access tokens, rotating
  refresh tokens stored in Postgres, automatic refresh on the frontend.
- **Market data** — scheduled Sharesansar scrape every 90 s during NEPSE hours (Sun–Thu
  11:00–15:00 NPT), header-based table parsing, cached fallback when the source fails, scrape-run
  audit log.
- **Portfolio** — buy/sell trades with fees, FIFO lot matching, remaining-lot average cost,
  realized and unrealized P&L, day change, allocation weights, transaction history.
- **Charts** — portfolio value history, allocation donut, per-stock price chart with 1M/3M/6M/1Y
  ranges.
- **Averaging engine** — configurable −5% / −10% / −15% trigger levels, tranche budgets, position
  and per-stock capital caps, plus an interactive "by quantity / by budget / target average"
  simulator with feasibility checks.
- **Recommendations** — weighted technical score (trend 30, RSI 20, momentum 20, position 15,
  volume 10, concentration 5) with human-readable reasons. Uses the ML service when a trained model
  is available, otherwise falls back to the Java rule engine.
- **Price alerts** — above, below, and daily percentage move alerts; evaluated on each scrape;
  one-shot with re-arm; live SSE feed and persisted event history.
- **Reports** — realized gains by FIFO lot, holding period, estimated capital gains tax at
  configurable short/long rates, CSV export.
- **Contact** — validated message stored in the database; optional SMTP email to the admin.
- **UX** — dark/light theme, responsive layout, animated page/card transitions, skeletons, toasts,
  accessible forms, live market ticker.

---

## Prerequisites

- Docker Engine + Docker Compose v2
- (Optional, for local development) JDK 21+, Node 20+, Python 3.12+

### Install Docker (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"   # log out/in or run: newgrp docker
docker version
```

If `docker info` reports *permission denied* or *cannot connect to the Docker daemon*, the daemon
is not running — start it with `sudo systemctl enable --now docker`.

### Install Node and Java (local dev only)

```bash
# Node 20+ (nvm recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
nvm install 20

# JDK 21
sudo apt install -y openjdk-21-jdk
java -version
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

| Service | URL |
| --- | --- |
| App | http://localhost:3000 |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| ML docs | http://localhost:8000/docs |
| Postgres | `localhost:5432` (user/db from `.env`) |

First startup runs Flyway, seeds 40 NEPSE symbols, 260 days of **clearly labelled seeded** price
history, a demo user, demo trades and alerts. Give the backend ~20–40 s on the first run.

### Demo accounts

| Email | Password | Role |
| --- | --- | --- |
| `demo@nepse.local` | `demo1234` | USER |
| `admin@nepse.local` | `admin1234` | ADMIN |

Change or disable these with `SEED_DEMO_USER=false` before any real deployment.

### Stop / reset

```bash
docker compose down          # keep data
docker compose down -v       # delete database and model volumes
```

---

## ML model

The ML service starts without a model and reports `modelLoaded: false`; the backend then uses the
rule engine, so recommendations work from day one.

Once the database has price history, train the model:

```bash
curl -X POST http://localhost:8000/train
curl http://localhost:8000/model/status
```

Or `make train-ml`. The model is a `RandomForestClassifier` over SMA20/SMA50 gap, RSI-14, 1/7/30-day
returns, 20-day volatility, volume ratio and daily range. Labels are 5-class forward-5-day return
buckets. The artifact is stored in the `ml_models` volume and reloaded on restart.

`POST /predict` takes `{symbol, lossPercent, holdingDays, concentration}`. The backend passes the
user's position context and falls back to Java scoring if the service is down, the symbol has too
little history, or the top-class probability is below `ML_MIN_CONFIDENCE` (default `0.35`). A
low-confidence model should not override a transparent rule score; on the seeded random-walk data
the model is deliberately treated this way until real history improves it.

---

## Scraper notes

- Source: `https://www.sharesansar.com/live-trading` (Jsoup, `#DataTables_Table_0`).
- Columns are mapped by header text, not position, so added columns do not break parsing.
- Runs every 90 s but only inside NEPSE hours by default. For demos outside market hours set
  `SCRAPER_IGNORE_MARKET_HOURS=true`.
- Every run is recorded in `scrape_runs`; the last good prices are kept if the request fails.
- `POST /api/admin/market/sync` triggers a manual scrape (ADMIN only).
- NEPSE has no dependable free historical API. Until enough real sessions accumulate, seeded bars
  are marked `source = SEEDED`; scraped stocks are marked `SCRAPED`. The UI shows this badge.

---

## Local development (without Docker for the app)

Start just Postgres:

```bash
docker compose up -d postgres
```

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Frontend (proxies `/api` to `localhost:8080`):

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

ML service:

```bash
cd ml-service
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --port 8000
```

Environment variables for local runs match `.env.example`; defaults in `application.yml` target
`localhost:5432`, `localhost:8000`, and `localhost:5173`.

---

## Tests

```bash
make backend-test    # JUnit: FIFO, indicators, DCA, scraper parsing
make frontend-test   # Vitest: formatting helpers
make ml-test         # pytest: feature/label engineering
```

Current baseline: backend 14 tests, frontend 6 tests, ML feature tests (3 tests). After
`docker compose up`, also run `make smoke` (or `bash tests/api-smoke.sh`) to verify the deployed
API, demo login, seeded portfolio and reports.

---

## API overview

All routes below are under `/api`; everything except auth, public market reads and contact requires
`Authorization: Bearer <accessToken>`.

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` |
| Market | `GET /market/stocks`, `GET /market/stocks/{symbol}?range=1M`, `GET /market/movers`, `GET /market/sectors` |
| Portfolio | `GET /portfolio`, `GET /portfolio/trades`, `POST /portfolio/trades`, `DELETE /portfolio/trades/{id}`, `GET /portfolio/history?days=365` |
| Analysis | `GET /analysis/recommendations`, `GET /analysis/stocks/{symbol}`, `GET /analysis/opportunities`, `POST /analysis/average-simulation` |
| Alerts | `GET/POST /alerts`, `PUT/DELETE /alerts/{id}`, `GET /alerts/events` |
| Reports | `GET /reports/realized-gains?from=&to=&format=csv` |
| Settings | `GET/PUT /settings` |
| Contact | `POST /contact` |
| Streams | `GET /market/stream`, `GET /alerts/stream?access_token=…` (SSE) |
| Admin | `GET /admin/market/status`, `POST /admin/market/sync`, `GET /admin/ml/status` |

Error responses use a consistent `{timestamp, status, error, message, path}` body. Validation errors
include a `fieldErrors` map.

---

## Configuration

Key variables (see `.env.example` and `backend/src/main/resources/application.yml`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | dev value | HMAC key, **must** be replaced in production (32+ chars) |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | 30 / 14 | Token lifetimes |
| `CORS_ORIGINS` | localhost:5173,3000 | Comma-separated allowed origins |
| `SCRAPER_ENABLED` | true | Toggle scraping |
| `SCRAPER_INTERVAL_MS` | 90000 | Scrape cadence |
| `SCRAPER_IGNORE_MARKET_HOURS` | false | Demo mode outside NEPSE hours |
| `ML_ENABLED` / `ML_SERVICE_URL` | true / localhost:8000 | ML integration |
| `ML_MIN_CONFIDENCE` | 0.35 | Minimum model probability before it overrides the rules |
| `SEED_DEMO_USER` | true | Create demo/admin accounts and sample trades |
| `MAIL_*`, `ADMIN_EMAIL`, `MAIL_FROM` | blank | Optional contact-form email |
| `SEED_PRICE_HISTORY_DAYS` | 260 | Seeded daily bars per symbol |

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
│   └── src/main/resources/db/migration/   # Flyway V1 schema, V2 stock seed
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
`/api/admin/market/status`. Seeded prices remain usable meanwhile.

**Recommendations show `RULE` instead of `ML`** — expected until you call `POST /train`; the rule
engine is the intentional fallback.

**SSE disconnects behind a proxy** — keep `proxy_buffering off` and a long `proxy_read_timeout`
(already configured in `frontend/nginx.conf`). The alerts stream authenticates with
`?access_token=` because native EventSource cannot set headers.

---

## Security and disclaimer

- The SPA stores the access and refresh tokens in `localStorage` for simplicity and refreshes the
  access token automatically; refresh tokens are also persisted server-side and rotated on use. For
  a hardened deployment, move tokens to httpOnly cookies and add a strict Content-Security-Policy.
- Passwords are BCrypt-hashed. Never commit `.env` or use the demo credentials in production.
- Run behind TLS, restrict CORS, rotate `JWT_SECRET`, and disable demo seeding for real deployments.
- This project is for education and personal record-keeping. It is **not** a brokerage, does not
  place orders, and does not provide financial, tax or investment advice. Verify every number
  against your broker and consult a licensed professional before acting.
