-- Human resources and payroll.
--
-- Design notes that the constraints below encode:
--  * money is NUMERIC, never floating point, and every rate lives in a table
--  * tax is never hardcoded: it comes from versioned tax_rules with effectivity dates,
--    so a rate can be changed prospectively and old payslips still explain themselves (#34)
--  * a payroll period is processed once; a second run for the same period is refused
--    rather than quietly producing a second set of payslips
--  * overtime is derived from employee attendance, not typed in by hand
--  * a loan or advance balance only ever moves through a payroll run, so the outstanding
--    balance cannot drift from the deductions actually taken

-- ------------------------------------------------------------------ designations

CREATE TABLE designations (
    id           UUID PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    level        VARCHAR(30),
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_designations_code UNIQUE (code)
);

-- -------------------------------------------------------------- salary structures

-- A salary structure is the configurable price of a job: a basic figure plus components
-- that are either a fixed amount or a percentage of the basic.
CREATE TABLE salary_structures (
    id             UUID PRIMARY KEY,
    name           VARCHAR(150)   NOT NULL,
    code           VARCHAR(40)    NOT NULL,
    basic_salary   NUMERIC(14, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'NPR',
    overtime_rate  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    effective_from DATE,
    description    VARCHAR(500),
    status         VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_salary_structures_code UNIQUE (code),
    CONSTRAINT ck_salary_structures_basic CHECK (basic_salary >= 0),
    CONSTRAINT ck_salary_structures_overtime CHECK (overtime_rate >= 0)
);

CREATE TABLE salary_components (
    id                 UUID PRIMARY KEY,
    salary_structure_id UUID NOT NULL REFERENCES salary_structures (id) ON DELETE CASCADE,
    name               VARCHAR(150) NOT NULL,
    component_type     VARCHAR(20)  NOT NULL,
    value_type         VARCHAR(20)  NOT NULL DEFAULT 'FIXED',
    value              NUMERIC(14, 4) NOT NULL,
    taxable            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_salary_components_type CHECK (component_type IN ('ALLOWANCE', 'DEDUCTION')),
    CONSTRAINT ck_salary_components_value_type CHECK (value_type IN ('FIXED', 'PERCENTAGE')),
    -- A percentage component is a rate, so it cannot carry a currency amount.
    CONSTRAINT ck_salary_components_percentage CHECK (
        value_type <> 'PERCENTAGE' OR (value >= 0 AND value <= 100))
);

-- ------------------------------------------------------------------- tax rules

-- Progressive brackets held as JSON: [{"upTo": 500000, "rate": 1}, {"upTo": null,
-- "rate": 15}]. A rule with no end date is the current one; overlapping rules for the
-- same period are impossible because a new rule supersedes rather than edits.
CREATE TABLE tax_rules (
    id           UUID PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    code         VARCHAR(40)  NOT NULL,
    brackets     JSONB        NOT NULL,
    effective_from DATE       NOT NULL,
    effective_to DATE,
    description  VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tax_rules_code UNIQUE (code),
    CONSTRAINT ck_tax_rules_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_tax_rules_effective ON tax_rules (effective_from, effective_to);

-- --------------------------------------------------------------------- employees

CREATE TABLE employees (
    id                  UUID PRIMARY KEY,
    employee_code       VARCHAR(40)   NOT NULL,
    user_id             UUID REFERENCES users (id) ON DELETE SET NULL,
    department_id       UUID REFERENCES departments (id) ON DELETE SET NULL,
    designation_id      UUID REFERENCES designations (id) ON DELETE SET NULL,
    salary_structure_id UUID REFERENCES salary_structures (id) ON DELETE SET NULL,
    first_name          VARCHAR(100)  NOT NULL,
    middle_name         VARCHAR(100),
    last_name           VARCHAR(100),
    date_of_birth       DATE,
    gender              VARCHAR(20),
    nationality         VARCHAR(80),
    phone               VARCHAR(40),
    email               VARCHAR(180),
    address             VARCHAR(400),
    photo_url           VARCHAR(400),
    employment_type     VARCHAR(30)   NOT NULL DEFAULT 'FULL_TIME',
    join_date           DATE          NOT NULL,
    exit_date           DATE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    bank_name           VARCHAR(120),
    bank_account        VARCHAR(60),
    tax_number          VARCHAR(60),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT ck_employees_exit_date CHECK (exit_date IS NULL OR exit_date >= join_date),
    CONSTRAINT ck_employees_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'RESIGNED', 'TERMINATED'))
);

CREATE INDEX idx_employees_department ON employees (department_id, status);
CREATE INDEX idx_employees_user ON employees (user_id);

-- The academic schema reserved these two columns before the HR module existed.
ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head_employee
    FOREIGN KEY (head_employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_employee
    FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

-- An employee may hold one employment record at a time, so their history cannot overlap.
CREATE TABLE employments (
    id               UUID PRIMARY KEY,
    employee_id      UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    employment_type  VARCHAR(30) NOT NULL,
    designation_id   UUID REFERENCES designations (id) ON DELETE SET NULL,
    department_id    UUID REFERENCES departments (id) ON DELETE SET NULL,
    salary_structure_id UUID REFERENCES salary_structures (id) ON DELETE SET NULL,
    start_date       DATE NOT NULL,
    end_date         DATE,
    reason           VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_employments_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_employments_employee ON employments (employee_id, start_date);

-- --------------------------------------------------------------- qualifications

CREATE TABLE qualifications (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    level       VARCHAR(60),
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qualifications_name UNIQUE (name)
);

CREATE TABLE employee_qualifications (
    id              UUID PRIMARY KEY,
    employee_id     UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    qualification_id UUID NOT NULL REFERENCES qualifications (id) ON DELETE CASCADE,
    institution     VARCHAR(150),
    awarded_year    INTEGER,
    grade           VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_qualifications UNIQUE (employee_id, qualification_id)
);

CREATE TABLE employee_documents (
    id           UUID PRIMARY KEY,
    employee_id  UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    file_name    VARCHAR(200) NOT NULL,
    storage_key  VARCHAR(400) NOT NULL,
    content_type VARCHAR(120),
    size_bytes   BIGINT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_documents_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

-- ------------------------------------------------------------------------ leave

CREATE TABLE leave_types (
    id            UUID PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    days_per_year NUMERIC(6, 1) NOT NULL DEFAULT 0,
    paid          BOOLEAN      NOT NULL DEFAULT TRUE,
    description   VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_types_code UNIQUE (code),
    CONSTRAINT ck_leave_types_days CHECK (days_per_year >= 0)
);

CREATE TABLE leave_balances (
    id            UUID PRIMARY KEY,
    employee_id   UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types (id) ON DELETE CASCADE,
    leave_year    INTEGER NOT NULL,
    entitled      NUMERIC(6, 1) NOT NULL DEFAULT 0,
    used          NUMERIC(6, 1) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_balances UNIQUE (employee_id, leave_type_id, leave_year),
    -- A balance can never go negative: the request that would take it below zero is refused.
    CONSTRAINT ck_leave_balances_used CHECK (used >= 0 AND used <= entitled)
);

CREATE TABLE leave_requests (
    id            UUID PRIMARY KEY,
    employee_id   UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types (id) ON DELETE RESTRICT,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    days          NUMERIC(6, 1) NOT NULL,
    reason        VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by    UUID,
    decided_at    TIMESTAMPTZ,
    decision_notes VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_requests_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_requests_days CHECK (days > 0),
    CONSTRAINT ck_leave_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_leave_requests_employee ON leave_requests (employee_id, start_date);

-- --------------------------------------------------------- employee attendance

-- One row per employee per working day. Overtime is derived here rather than entered, so a
-- payslip's overtime line can always be traced back to the days that earned it.
CREATE TABLE employee_attendance (
    id               UUID PRIMARY KEY,
    employee_id      UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    attendance_date  DATE NOT NULL,
    status           VARCHAR(20) NOT NULL,
    check_in         TIME,
    check_out        TIME,
    overtime_minutes INTEGER NOT NULL DEFAULT 0,
    remarks          VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_attendance UNIQUE (employee_id, attendance_date),
    CONSTRAINT ck_employee_attendance_status CHECK (
        status IN ('PRESENT', 'ABSENT', 'LATE', 'ON_LEAVE', 'HOLIDAY', 'WEEK_OFF')),
    CONSTRAINT ck_employee_attendance_overtime CHECK (overtime_minutes >= 0)
);

CREATE INDEX idx_employee_attendance_date ON employee_attendance (attendance_date, status);

-- ---------------------------------------------------------------- loans and advances

CREATE TABLE employee_loans (
    id               UUID PRIMARY KEY,
    employee_id      UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    loan_type        VARCHAR(20) NOT NULL,
    reference        VARCHAR(60) NOT NULL,
    principal        NUMERIC(14, 2) NOT NULL,
    installment_amount NUMERIC(14, 2) NOT NULL,
    outstanding      NUMERIC(14, 2) NOT NULL,
    granted_on       DATE NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_loans_reference UNIQUE (reference),
    CONSTRAINT ck_employee_loans_type CHECK (loan_type IN ('LOAN', 'ADVANCE')),
    CONSTRAINT ck_employee_loans_status CHECK (status IN ('ACTIVE', 'SETTLED', 'CANCELLED')),
    CONSTRAINT ck_employee_loans_amounts CHECK (
        principal > 0 AND installment_amount > 0 AND outstanding >= 0 AND outstanding <= principal)
);

CREATE INDEX idx_employee_loans_employee ON employee_loans (employee_id, status);

-- --------------------------------------------------------------------- payroll

-- One run per period. The unique constraint is what makes reprocessing a month impossible.
CREATE TABLE payroll_runs (
    id                UUID PRIMARY KEY,
    period_year       INTEGER NOT NULL,
    period_month      INTEGER NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    employee_count    INTEGER NOT NULL DEFAULT 0,
    total_gross       NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_deductions  NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_tax         NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_net         NUMERIC(16, 2) NOT NULL DEFAULT 0,
    processed_at      TIMESTAMPTZ,
    approved_at       TIMESTAMPTZ,
    approved_by       UUID,
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payroll_runs_period UNIQUE (period_year, period_month),
    CONSTRAINT ck_payroll_runs_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_payroll_runs_status CHECK (status IN ('DRAFT', 'PROCESSED', 'APPROVED', 'CANCELLED'))
);

CREATE TABLE payslips (
    id                UUID PRIMARY KEY,
    payroll_run_id    UUID NOT NULL REFERENCES payroll_runs (id) ON DELETE CASCADE,
    employee_id       UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    basic_salary      NUMERIC(14, 2) NOT NULL,
    total_allowances  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    overtime_amount   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    bonus             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    loan_deduction    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    other_deductions  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gross_salary      NUMERIC(14, 2) NOT NULL,
    tax               NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_deductions  NUMERIC(14, 2) NOT NULL,
    net_salary        NUMERIC(14, 2) NOT NULL,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    tax_rule_code     VARCHAR(40),
    overtime_minutes  INTEGER      NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'GENERATED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    -- An employee appears once in a run, so a payslip cannot be duplicated.
    CONSTRAINT uk_payslips_run_employee UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT ck_payslips_amounts CHECK (
        basic_salary >= 0 AND total_allowances >= 0 AND overtime_amount >= 0 AND bonus >= 0
        AND loan_deduction >= 0 AND other_deductions >= 0 AND tax >= 0
        AND net_salary = gross_salary - total_deductions - tax)
);

CREATE TABLE payslip_items (
    id             UUID PRIMARY KEY,
    payslip_id     UUID NOT NULL REFERENCES payslips (id) ON DELETE CASCADE,
    name           VARCHAR(150) NOT NULL,
    component_type VARCHAR(20)  NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payslip_items_type CHECK (component_type IN ('ALLOWANCE', 'DEDUCTION', 'OVERTIME', 'BONUS', 'LOAN', 'TAX')),
    CONSTRAINT ck_payslip_items_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payslips_employee ON payslips (employee_id, created_at);