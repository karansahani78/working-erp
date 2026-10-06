-- Finance and accounting.
--
-- Design notes that the constraints below encode:
--  * money is stored as NUMERIC, never floating point
--  * a confirmed payment is never deleted; it is refunded or reversed (#76)
--  * payments are idempotent on provider transaction / reference (#78)
--  * discounts and scholarships reduce what is assessed, but are recorded separately so
--    the gross fee, the concession and the net payable are all auditable

-- ---------------------------------------------------------------- fee structures

CREATE TABLE fee_structures (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(40)  NOT NULL,
    program_id         UUID REFERENCES programs (id) ON DELETE CASCADE,
    school_class_id    UUID REFERENCES school_classes (id) ON DELETE CASCADE,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE CASCADE,
    total_amount       NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    installments_count INTEGER      NOT NULL DEFAULT 1,
    description        VARCHAR(500),
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fee_structures_code UNIQUE (code),
    CONSTRAINT ck_fee_structures_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_fee_structures_installments CHECK (installments_count >= 1)
);

CREATE INDEX idx_fee_structures_scope ON fee_structures (program_id, school_class_id, academic_year_id);

CREATE TABLE fee_components (
    id                 UUID PRIMARY KEY,
    fee_structure_id   UUID NOT NULL REFERENCES fee_structures (id) ON DELETE CASCADE,
    name               VARCHAR(150) NOT NULL,
    component_type     VARCHAR(30)  NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    mandatory          BOOLEAN      NOT NULL DEFAULT TRUE,
    description        VARCHAR(300),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fee_components_amount CHECK (amount >= 0)
);

CREATE INDEX idx_fee_components_structure ON fee_components (fee_structure_id);

-- ---------------------------------------------------------------- concessions

CREATE TABLE discounts (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(40)  NOT NULL,
    discount_type      VARCHAR(20)  NOT NULL,
    value              NUMERIC(14, 2) NOT NULL,
    max_amount         NUMERIC(14, 2),
    applicable_to      VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    description        VARCHAR(500),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_discounts_code UNIQUE (code),
    CONSTRAINT ck_discounts_value CHECK (value > 0)
);

CREATE TABLE scholarships (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(40)  NOT NULL,
    award_type         VARCHAR(20)  NOT NULL,
    value              NUMERIC(14, 2) NOT NULL,
    max_amount         NUMERIC(14, 2),
    applicable_to      VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    description        VARCHAR(500),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_scholarships_code UNIQUE (code),
    CONSTRAINT ck_scholarships_value CHECK (value > 0)
);

CREATE TABLE student_concessions (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    discount_id        UUID REFERENCES discounts (id) ON DELETE SET NULL,
    scholarship_id     UUID REFERENCES scholarships (id) ON DELETE SET NULL,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    reason             VARCHAR(500),
    granted_by         UUID,
    granted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_student_concessions_amount CHECK (amount > 0),
    CONSTRAINT ck_student_concessions_source CHECK (
        (discount_id IS NOT NULL AND scholarship_id IS NULL)
            OR (discount_id IS NULL AND scholarship_id IS NOT NULL))
);

CREATE INDEX idx_student_concessions_student ON student_concessions (student_id, academic_year_id);

-- ---------------------------------------------------------------- assessment

CREATE TABLE student_fee_assessments (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    fee_structure_id   UUID NOT NULL REFERENCES fee_structures (id) ON DELETE RESTRICT,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    gross_amount       NUMERIC(14, 2) NOT NULL,
    discount_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    scholarship_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net_amount         NUMERIC(14, 2) NOT NULL,
    paid_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    refunded_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    due_date           DATE,
    notes              VARCHAR(500),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_assessments_student_structure UNIQUE (student_id, fee_structure_id),
    CONSTRAINT ck_assessment_amounts CHECK (
        gross_amount >= 0 AND discount_amount >= 0 AND scholarship_amount >= 0
        AND net_amount >= 0 AND paid_amount >= 0 AND refunded_amount >= 0),
    CONSTRAINT ck_assessment_net CHECK (net_amount = gross_amount - discount_amount - scholarship_amount)
);

CREATE INDEX idx_assessments_student ON student_fee_assessments (student_id, status);

-- ---------------------------------------------------------------- invoices

CREATE TABLE invoices (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
    assessment_id      UUID REFERENCES student_fee_assessments (id) ON DELETE SET NULL,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    invoice_number     VARCHAR(40)  NOT NULL,
    issue_date         DATE         NOT NULL,
    due_date           DATE,
    total_amount       NUMERIC(14, 2) NOT NULL,
    paid_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    notes              VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoice_amounts CHECK (total_amount >= 0 AND paid_amount >= 0)
);

CREATE INDEX idx_invoices_student ON invoices (student_id, status);

CREATE TABLE invoice_items (
    id                 UUID PRIMARY KEY,
    invoice_id         UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    description        VARCHAR(200) NOT NULL,
    component_type     VARCHAR(30)  NOT NULL DEFAULT 'TUITION',
    amount             NUMERIC(14, 2) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoice_items_amount CHECK (amount >= 0)
);

CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

-- ---------------------------------------------------------------- payments

CREATE TABLE payments (
    id                       UUID PRIMARY KEY,
    student_id               UUID NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
    invoice_id               UUID REFERENCES invoices (id) ON DELETE SET NULL,
    assessment_id            UUID REFERENCES student_fee_assessments (id) ON DELETE SET NULL,
    receipt_number           VARCHAR(40)  NOT NULL,
    amount                   NUMERIC(14, 2) NOT NULL,
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    method                   VARCHAR(20)  NOT NULL,
    provider                 VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    provider_transaction_id  VARCHAR(120),
    idempotency_key          VARCHAR(120),
    reference                VARCHAR(120),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    failure_reason           VARCHAR(300),
    received_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_receipt UNIQUE (receipt_number),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);

-- #78: a provider callback replayed must not create a second payment.
CREATE UNIQUE INDEX uk_payments_provider_txn
    ON payments (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE UNIQUE INDEX uk_payments_idempotency
    ON payments (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_payments_student ON payments (student_id, status);
CREATE INDEX idx_payments_invoice ON payments (invoice_id);

-- Refunds point at the original payment; a confirmed payment is never deleted (#76).
CREATE TABLE refunds (
    id                 UUID PRIMARY KEY,
    payment_id         UUID NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
    amount             NUMERIC(14, 2) NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    method             VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_by       UUID,
    approved_by        UUID,
    approved_at        TIMESTAMPTZ,
    processed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);

CREATE TABLE fines (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    description        VARCHAR(200) NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fines_amount CHECK (amount > 0)
);

CREATE INDEX idx_fines_student ON fines (student_id, status);

-- ---------------------------------------------------------------- accounting

CREATE TABLE chart_of_accounts (
    id                 UUID PRIMARY KEY,
    code               VARCHAR(20)  NOT NULL,
    name               VARCHAR(150) NOT NULL,
    account_group      VARCHAR(30)  NOT NULL,
    account_type       VARCHAR(20)  NOT NULL,
    parent_id          UUID REFERENCES chart_of_accounts (id) ON DELETE SET NULL,
    is_postable        BOOLEAN      NOT NULL DEFAULT TRUE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_chart_of_accounts_code UNIQUE (code)
);

CREATE TABLE fiscal_years (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    code               VARCHAR(20)  NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fiscal_years_code UNIQUE (code),
    CONSTRAINT ck_fiscal_years_order CHECK (end_date > start_date)
);

CREATE TABLE accounting_periods (
    id                 UUID PRIMARY KEY,
    fiscal_year_id     UUID NOT NULL REFERENCES fiscal_years (id) ON DELETE CASCADE,
    name               VARCHAR(80)  NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accounting_periods_name UNIQUE (fiscal_year_id, name),
    CONSTRAINT ck_accounting_periods_order CHECK (end_date > start_date)
);

CREATE TABLE bank_accounts (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    bank_name          VARCHAR(150) NOT NULL,
    account_number     VARCHAR(60)  NOT NULL,
    chart_account_id   UUID REFERENCES chart_of_accounts (id) ON DELETE SET NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bank_accounts_number UNIQUE (bank_name, account_number)
);

-- A journal entry is immutable once posted, so posted_at is set exactly once and
-- there is no way to alter the lines of a POSTED entry.
CREATE TABLE journal_entries (
    id                 UUID PRIMARY KEY,
    entry_number       VARCHAR(40)  NOT NULL,
    entry_date         DATE         NOT NULL,
    fiscal_year_id     UUID REFERENCES fiscal_years (id) ON DELETE SET NULL,
    accounting_period_id UUID REFERENCES accounting_periods (id) ON DELETE SET NULL,
    source_type        VARCHAR(30)  NOT NULL,
    source_id          UUID,
    description        VARCHAR(300),
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    posted_at          TIMESTAMPTZ,
    reversed_by_id     UUID REFERENCES journal_entries (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_journal_entries_number UNIQUE (entry_number)
);

CREATE INDEX idx_journal_entries_date ON journal_entries (entry_date);
CREATE INDEX idx_journal_entries_source ON journal_entries (source_type, source_id);

CREATE TABLE journal_lines (
    id                 UUID PRIMARY KEY,
    journal_entry_id   UUID NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    account_id         UUID NOT NULL REFERENCES chart_of_accounts (id) ON DELETE RESTRICT,
    description        VARCHAR(200),
    debit              NUMERIC(14, 2) NOT NULL DEFAULT 0,
    credit             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    party_type         VARCHAR(20),
    party_id           UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    -- A line is either a debit or a credit, never both and never neither.
    CONSTRAINT ck_journal_lines_one_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX idx_journal_lines_entry ON journal_lines (journal_entry_id);
CREATE INDEX idx_journal_lines_account ON journal_lines (account_id);

CREATE TABLE bank_reconciliations (
    id                 UUID PRIMARY KEY,
    bank_account_id    UUID NOT NULL REFERENCES bank_accounts (id) ON DELETE CASCADE,
    statement_date     DATE NOT NULL,
    statement_ending_balance NUMERIC(14, 2) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    reconciled_by      UUID,
    reconciled_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

-- ------------------------------------------------------- document numbering
-- One counter per document kind (INVOICE, RECEIPT, JOURNAL). Incremented under a row
-- lock so two concurrent payments can never be issued the same receipt number, and
-- scoped by period so a new financial year restarts the series.

CREATE TABLE document_sequences (
    id                 UUID PRIMARY KEY,
    document_kind      VARCHAR(30) NOT NULL,
    period             VARCHAR(20) NOT NULL,
    current_value      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_sequences_kind_period UNIQUE (document_kind, period),
    CONSTRAINT ck_document_sequences_value CHECK (current_value >= 0)
);
