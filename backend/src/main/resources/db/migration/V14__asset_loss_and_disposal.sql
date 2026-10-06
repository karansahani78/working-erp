-- Losing and disposing of an asset are the two register endings that had a status but no
-- way to reach it. Each needs to remember what happened, not just that it happened.

ALTER TABLE assets
    ADD COLUMN lost_at TIMESTAMPTZ,
    ADD COLUMN lost_reason VARCHAR(500),
    ADD COLUMN disposed_at TIMESTAMPTZ,
    ADD COLUMN disposal_method VARCHAR(40),
    ADD COLUMN disposal_notes VARCHAR(500),
    ADD COLUMN disposal_value NUMERIC(14, 2);

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_disposal_method CHECK (
        disposal_method IS NULL OR
        disposal_method IN ('SALE', 'RECYCLE', 'DONATION', 'SCRAPPED', 'WRITE_OFF')),
    ADD CONSTRAINT ck_assets_disposal_value CHECK (
        disposal_value IS NULL OR disposal_value >= 0),
    ADD CONSTRAINT ck_assets_disposed_shape CHECK (
        (status = 'DISPOSED') = (disposed_at IS NOT NULL));
