-- Links an approved payroll run to the journal entry it produced, so a month of salary
-- can be posted to the ledger exactly once. The column is deliberately nullable: a run is
-- approved before it is booked, and institutions may not even use the accounting module.
ALTER TABLE payroll_runs ADD COLUMN journal_entry_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payroll_runs_journal_entry') THEN
        ALTER TABLE payroll_runs
            ADD CONSTRAINT fk_payroll_runs_journal_entry
                FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_payroll_runs_journal_entry
    ON payroll_runs (journal_entry_id)
    WHERE journal_entry_id IS NOT NULL;

-- The ledger entry is part of the audit trail of a run: an approved run whose entry has
-- been reversed is no longer a truthful record, so it is flagged rather than silently kept.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_payroll_runs_totals') THEN
        ALTER TABLE payroll_runs
            ADD CONSTRAINT ck_payroll_runs_totals CHECK (
                total_gross >= 0
                AND total_deductions >= 0
                AND total_tax >= 0
                AND total_net >= 0
                AND total_net = total_gross - total_deductions - total_tax
            );
    END IF;
END
$$;