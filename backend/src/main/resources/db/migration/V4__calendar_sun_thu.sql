-- NEPSE's trading week is Sunday–Thursday, not Monday–Friday. The V2 seed
-- (and every DB that applied it) carried the wrong week as the fallback for
-- when NEPSE's own market-status endpoint is unreachable. Stored values use
-- either full names or 3-letter abbreviations, so detect the abbreviated
-- versions precisely (MON is a prefix of MONDAY, so plain LIKE would lie).

UPDATE app_settings
SET setting_value = '{"tradingDays":["MON","TUE","WED","THU","SUN"],"openTime":"11:00","closeTime":"15:00"}'
WHERE setting_key = 'market-calendar'
  AND setting_value LIKE '%WED%'
  AND setting_value NOT LIKE '%WEDNESDAY%';
