-- NEPSE portfolio tracker schema (PostgreSQL)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(120) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE stocks (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20) NOT NULL UNIQUE,
    company_name    VARCHAR(255) NOT NULL,
    sector          VARCHAR(120),
    ltp             NUMERIC(18,2) NOT NULL DEFAULT 0,
    prev_close      NUMERIC(18,2) NOT NULL DEFAULT 0,
    open_price      NUMERIC(18,2) NOT NULL DEFAULT 0,
    high_price      NUMERIC(18,2) NOT NULL DEFAULT 0,
    low_price       NUMERIC(18,2) NOT NULL DEFAULT 0,
    volume          BIGINT NOT NULL DEFAULT 0,
    turnover        NUMERIC(20,2) NOT NULL DEFAULT 0,
    market_cap      NUMERIC(24,2),
    pe_ratio        NUMERIC(10,2),
    eps             NUMERIC(10,2),
    source          VARCHAR(20) NOT NULL DEFAULT 'SEEDED',
    last_updated    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stocks_sector ON stocks(sector);

CREATE TABLE price_history (
    id           BIGSERIAL PRIMARY KEY,
    stock_id     BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    trade_date   DATE NOT NULL,
    open_price   NUMERIC(18,2) NOT NULL,
    high_price   NUMERIC(18,2) NOT NULL,
    low_price    NUMERIC(18,2) NOT NULL,
    close_price  NUMERIC(18,2) NOT NULL,
    volume       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_history_stock_date UNIQUE (stock_id, trade_date)
);
CREATE INDEX idx_price_history_stock_date ON price_history(stock_id, trade_date DESC);

CREATE TABLE trades (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_id    BIGINT NOT NULL REFERENCES stocks(id),
    type        VARCHAR(4) NOT NULL CHECK (type IN ('BUY', 'SELL')),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    price       NUMERIC(18,2) NOT NULL CHECK (price > 0),
    brokerage   NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax         NUMERIC(18,2) NOT NULL DEFAULT 0,
    trade_date  TIMESTAMPTZ NOT NULL,
    notes       VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trades_user ON trades(user_id, trade_date);
CREATE INDEX idx_trades_user_stock ON trades(user_id, stock_id, trade_date);

CREATE TABLE alerts (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_id           BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    type               VARCHAR(20) NOT NULL CHECK (type IN ('ABOVE', 'BELOW', 'PERCENT_CHANGE')),
    threshold          NUMERIC(18,2) NOT NULL CHECK (threshold > 0),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    triggered          BOOLEAN NOT NULL DEFAULT FALSE,
    last_triggered_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alerts_user_active ON alerts(user_id, active);
CREATE INDEX idx_alerts_stock ON alerts(stock_id);

CREATE TABLE alert_events (
    id          BIGSERIAL PRIMARY KEY,
    alert_id    BIGINT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_id    BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    price       NUMERIC(18,2) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alert_events_user ON alert_events(user_id, created_at DESC);

CREATE TABLE user_settings (
    user_id                     BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    theme                       VARCHAR(10) NOT NULL DEFAULT 'dark',
    loss_threshold_minor        NUMERIC(6,2) NOT NULL DEFAULT 5,
    loss_threshold_major        NUMERIC(6,2) NOT NULL DEFAULT 10,
    loss_threshold_aggressive   NUMERIC(6,2) NOT NULL DEFAULT 15,
    base_buy_amount             NUMERIC(18,2) NOT NULL DEFAULT 10000,
    max_position_percent        NUMERIC(6,2) NOT NULL DEFAULT 25,
    max_capital_per_stock       NUMERIC(18,2) NOT NULL DEFAULT 100000,
    cgt_short_rate              NUMERIC(6,2) NOT NULL DEFAULT 7.5,
    cgt_long_rate               NUMERIC(6,2) NOT NULL DEFAULT 5.0,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE contact_messages (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    message     VARCHAR(4000) NOT NULL,
    email_sent  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE scrape_runs (
    id           BIGSERIAL PRIMARY KEY,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at  TIMESTAMPTZ,
    success      BOOLEAN NOT NULL DEFAULT FALSE,
    rows_parsed  INTEGER NOT NULL DEFAULT 0,
    source       VARCHAR(60) NOT NULL,
    message      VARCHAR(1000)
);
CREATE INDEX idx_scrape_runs_started ON scrape_runs(started_at DESC);

CREATE TABLE ml_model_runs (
    id             BIGSERIAL PRIMARY KEY,
    trained_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    samples        INTEGER NOT NULL DEFAULT 0,
    accuracy       NUMERIC(6,4),
    model_version  VARCHAR(80),
    status         VARCHAR(40) NOT NULL,
    message        VARCHAR(1000)
);
