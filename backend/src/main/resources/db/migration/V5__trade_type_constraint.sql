-- V3 introduced IPO/FPO/BONUS/RIGHT/AUCTION trade types in Java but left the
-- V1 column (VARCHAR(4)) and its BUY/SELL-only check constraint untouched.
-- Widen the column and replace the constraint so the new types can be stored.

ALTER TABLE trades ALTER COLUMN type TYPE VARCHAR(20);

ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_type_check;

ALTER TABLE trades ADD CONSTRAINT trades_type_check
    CHECK (type IN ('BUY', 'SELL', 'IPO', 'FPO', 'BONUS', 'RIGHT', 'AUCTION'));