-- Global, admin-editable application settings (key -> JSON value).

CREATE TABLE app_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(64) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Market calendar. NEPSE has changed its trading week before
-- (Sunday-Thursday, then Monday-Friday) and can change it again,
-- so the calendar is data, not code. Admins edit this row via
-- PUT /api/admin/market/calendar.
INSERT INTO app_settings (setting_key, setting_value)
VALUES ('market-calendar',
        '{"tradingDays":["MON","TUE","WED","THU","FRI"],"openTime":"11:00","closeTime":"15:00"}');
