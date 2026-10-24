-- Trade types beyond buy/sell (IPO, FPO, BONUS, RIGHT, AUCTION) and the
-- finer-grained Nepali cost fields that come with them. Existing rows keep
-- behaving as BUY with their manually-entered fees.

ALTER TABLE trades ADD COLUMN sebon_fee NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE trades ADD COLUMN dp_charge NUMERIC(18,2) NOT NULL DEFAULT 0;

-- Per-user trading-rates overrides (partial JSON merged over the admin global row).
ALTER TABLE user_settings ADD COLUMN trading_rates_json TEXT;

-- Nepal's cost/tax schedule lives in configuration so it changes without a
-- deploy. Seed the verified defaults (see docs/nepse-costs.md).
INSERT INTO app_settings (setting_key, setting_value)
VALUES ('trading-rates',
'{"commissionTiers":[{"upTo":2500,"percent":null,"flat":10},'
'{"upTo":50000,"percent":0.36,"flat":null},'
'{"upTo":500000,"percent":0.33,"flat":null},'
'{"upTo":2000000,"percent":0.31,"flat":null},'
'{"upTo":null,"percent":0.30,"flat":null}],'
'"sebonFeePercent":0.015,'
'"dpChargePerScripPerDay":25,'
'"cgtShortRate":5.0,'
'"cgtLongRate":3.75,'
'"cgtLongThresholdDays":365,'
'"buyFeesIncludedInBasis":true}');

-- Budget 2083/84 reduced the share CGT slabs (10%/7.5% -> 5%/3.75%); migrate
-- users who never customized. Customized users keep whatever they entered.
UPDATE user_settings SET cgt_short_rate = 5.00, cgt_long_rate = 3.75
WHERE cgt_short_rate = 7.5 AND cgt_long_rate = 5.0;
