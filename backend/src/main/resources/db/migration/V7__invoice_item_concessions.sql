-- Concessions on an invoice.
--
-- invoice_items.amount is constrained to be >= 0, so a discount or scholarship cannot be
-- expressed as a negative line. Flag the line instead: the charge lines add up to gross and
-- the flagged lines come off it, which keeps the invoice total equal to the assessed net.

ALTER TABLE invoice_items
    ADD COLUMN is_concession BOOLEAN NOT NULL DEFAULT FALSE;

-- A concession is a discount or an award, never a charge under tuition.
ALTER TABLE invoice_items
    ADD CONSTRAINT ck_invoice_items_concession_type
    CHECK (is_concession = FALSE OR component_type IN ('ADMISSION', 'OTHER'));

COMMENT ON COLUMN invoice_items.is_concession IS
    'True when this line reduces the invoice total (discount or scholarship) rather than charging for it.';
