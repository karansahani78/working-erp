-- Phase 10: the library and its lending, the store and its stock, the assets the institution
-- owns, and the documents it keeps.
--
-- Two decisions worth stating up front.
--
-- First, nothing here stores a file. A document row holds metadata, a storage key and a
-- checksum; the bytes live in object storage behind a storage service. That is what lets a
-- document be previewed, versioned and served without pulling bytes through the database.
--
-- Second, every movement of stock and every change of an asset's hands is a row, not an
-- edit. Inventory and assets are both arguments about what the institution actually holds,
-- and an argument is only settled by history.

-- =========================================================================== library

CREATE TABLE library_categories (
    id                UUID PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_library_categories_code UNIQUE (code)
);

CREATE TABLE publishers (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    address           VARCHAR(400),
    contact_email     VARCHAR(180),
    contact_phone     VARCHAR(60),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_publishers_name UNIQUE (name)
);

CREATE TABLE authors (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    biography         TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE books (
    id                UUID PRIMARY KEY,
    isbn              VARCHAR(20),
    title             VARCHAR(300) NOT NULL,
    edition           VARCHAR(60),
    publication_year  INTEGER,
    language          VARCHAR(60),
    publisher_id      UUID REFERENCES publishers (id),
    category_id       UUID REFERENCES library_categories (id),
    call_number       VARCHAR(60),
    shelf_location    VARCHAR(80),
    cover_url         VARCHAR(400),
    description       TEXT,
    is_reference      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_books_isbn ON books (isbn) WHERE isbn IS NOT NULL;

CREATE INDEX idx_books_title ON books (lower(title));

CREATE INDEX idx_books_category ON books (category_id);

-- An author may write several books and a book may have several authors, so the names are
-- kept apart and joined rather than mashed into one row per book.
CREATE TABLE books_authors (
    book_id           UUID NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    author_id         UUID NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, author_id)
);

CREATE INDEX idx_books_authors_author ON books_authors (author_id);

-- A copy is the thing you lend. Title-level availability is derived from copies, never
-- stored, so availability cannot drift away from what is actually on the shelf.
CREATE TABLE book_copies (
    id                UUID PRIMARY KEY,
    book_id           UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    barcode           VARCHAR(60)  NOT NULL,
    acquisition_type  VARCHAR(20)  NOT NULL DEFAULT 'PURCHASE',
    acquired_on       DATE,
    price             NUMERIC(12, 2),
    condition_status  VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    status            VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_book_copies_barcode UNIQUE (barcode),
    CONSTRAINT ck_book_copies_acquisition
        CHECK (acquisition_type IN ('PURCHASE', 'DONATION', 'EXCHANGE')),
    CONSTRAINT ck_book_copies_condition
        CHECK (condition_status IN ('NEW', 'GOOD', 'WORN', 'DAMAGED')),
    CONSTRAINT ck_book_copies_status
        CHECK (status IN ('AVAILABLE', 'ISSUED', 'RESERVED', 'IN_REPAIR', 'WITHDRAWN', 'LOST'))
);

CREATE INDEX idx_book_copies_book ON book_copies (book_id);

CREATE INDEX idx_book_copies_status ON book_copies (status);

-- A member is somebody allowed to borrow: a student, a member of staff, or an outsider the
-- librarian registered. The person is referenced rather than copied so one human is one row
-- no matter how many of those roles they hold.
CREATE TABLE library_members (
    id                 UUID PRIMARY KEY,
    member_code        VARCHAR(40)  NOT NULL,
    user_id            UUID REFERENCES users (id) ON DELETE SET NULL,
    student_id         UUID REFERENCES students (id) ON DELETE SET NULL,
    employee_id        UUID REFERENCES employees (id) ON DELETE SET NULL,
    external_name      VARCHAR(200),
    external_phone     VARCHAR(60),
    external_email     VARCHAR(180),
    max_books          INTEGER       NOT NULL DEFAULT 3,
    membership_start   DATE          NOT NULL DEFAULT CURRENT_DATE,
    membership_end     DATE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_library_members_code UNIQUE (member_code),
    CONSTRAINT ck_library_members_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')),
    CONSTRAINT ck_library_members_limit CHECK (max_books > 0),
    CONSTRAINT ck_library_members_dates
        CHECK (membership_end IS NULL OR membership_end >= membership_start),
    -- A member has to be somebody. Copying a person into a row that points at nobody would
    -- create a membership that can never be matched back to a login.
    CONSTRAINT ck_library_members_identity CHECK (
        num_nonnulls(user_id, student_id, employee_id, external_name) >= 1
    )
);

CREATE UNIQUE INDEX uk_library_members_user
    ON library_members (user_id) WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uk_library_members_student
    ON library_members (student_id) WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX uk_library_members_employee
    ON library_members (employee_id) WHERE employee_id IS NOT NULL;

CREATE TABLE library_issues (
    id                 UUID PRIMARY KEY,
    copy_id            UUID        NOT NULL REFERENCES book_copies (id),
    member_id          UUID        NOT NULL REFERENCES library_members (id),
    issued_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    due_at             TIMESTAMPTZ  NOT NULL,
    returned_at        TIMESTAMPTZ,
    renewal_count      INTEGER     NOT NULL DEFAULT 0,
    fine_id            UUID,
    condition_out      VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    condition_in       VARCHAR(20),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_issues_status CHECK (status IN ('ISSUED', 'RETURNED', 'LOST')),
    CONSTRAINT ck_library_issues_dates CHECK (due_at > issued_at),
    CONSTRAINT ck_library_issues_returned
        CHECK ((status = 'RETURNED') = (returned_at IS NOT NULL)),
    CONSTRAINT ck_library_issues_renewals CHECK (renewal_count >= 0)
);

-- One copy is on loan to one person at a time. Enforced here rather than in the service
-- layer so a concurrent issue cannot hand out the same physical book twice.
CREATE UNIQUE INDEX uk_library_issues_copy_on_loan
    ON library_issues (copy_id)
    WHERE returned_at IS NULL;

CREATE INDEX idx_library_issues_member ON library_issues (member_id, status);

CREATE INDEX idx_library_issues_due ON library_issues (due_at) WHERE returned_at IS NULL;

CREATE TABLE library_renewals (
    id                 UUID PRIMARY KEY,
    issue_id           UUID        NOT NULL REFERENCES library_issues (id) ON DELETE CASCADE,
    renewed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    previous_due_at    TIMESTAMPTZ  NOT NULL,
    new_due_at         TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_renewals_dates CHECK (new_due_at > previous_due_at)
);

CREATE TABLE library_reservations (
    id                 UUID PRIMARY KEY,
    book_id            UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    member_id          UUID        NOT NULL REFERENCES library_members (id),
    reserved_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    fulfilled_issue_id UUID REFERENCES library_issues (id) ON DELETE SET NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    queue_position     INTEGER     NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_reservations_status
        CHECK (status IN ('WAITING', 'FULFILLED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_library_reservations_expiry CHECK (expires_at > reserved_at)
);

-- One person waits once per title. Two rows for the same book and member would make the
-- queue position meaningless.
CREATE UNIQUE INDEX uk_library_reservations_waiting
    ON library_reservations (book_id, member_id)
    WHERE status = 'WAITING';

CREATE TABLE library_fines (
    id                 UUID PRIMARY KEY,
    member_id          UUID        NOT NULL REFERENCES library_members (id),
    issue_id           UUID REFERENCES library_issues (id) ON DELETE SET NULL,
    reason             VARCHAR(300) NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(10)  NOT NULL DEFAULT 'NPR',
    assessed_on        DATE         NOT NULL DEFAULT CURRENT_DATE,
    paid_at            TIMESTAMPTZ,
    waived_at          TIMESTAMPTZ,
    waiver_reason      VARCHAR(300),
    status             VARCHAR(20)  NOT NULL DEFAULT 'OUTSTANDING',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_fines_status CHECK (status IN ('OUTSTANDING', 'PAID', 'WAIVED')),
    CONSTRAINT ck_library_fines_amount CHECK (amount >= 0),
    CONSTRAINT ck_library_fines_settled
        CHECK (status = 'OUTSTANDING' OR paid_at IS NOT NULL OR waived_at IS NOT NULL)
);

CREATE INDEX idx_library_fines_member ON library_fines (member_id, status);

-- ========================================================================= inventory

CREATE TABLE item_categories (
    id                UUID PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    parent_id         UUID REFERENCES item_categories (id),
    description       VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_item_categories_code UNIQUE (code),
    CONSTRAINT ck_item_categories_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE TABLE stores (
    id                UUID PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    campus_id         UUID REFERENCES campuses (id),
    keeper_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    address           VARCHAR(400),
    phone             VARCHAR(60),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_stores_code UNIQUE (code)
);

CREATE TABLE items (
    id                UUID PRIMARY KEY,
    code              VARCHAR(60)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       VARCHAR(500),
    category_id       UUID REFERENCES item_categories (id),
    unit              VARCHAR(30)  NOT NULL DEFAULT 'PCS',
    -- The level at which somebody should be told the shelf is running out.
    reorder_level     NUMERIC(12, 3) NOT NULL DEFAULT 0,
    reorder_quantity  NUMERIC(12, 3) NOT NULL DEFAULT 0,
    track_batch       BOOLEAN      NOT NULL DEFAULT FALSE,
    track_expiry      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_items_code UNIQUE (code),
    CONSTRAINT ck_items_reorder_levels CHECK (reorder_level >= 0 AND reorder_quantity >= 0)
);

CREATE INDEX idx_items_category ON items (category_id);

-- Quantity on hand per store, and nothing else. Every change to this number is explained by a
-- row in stock_movements, which is what makes the figure defensible.
CREATE TABLE stock (
    id                UUID PRIMARY KEY,
    item_id           UUID         NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    store_id          UUID         NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    quantity          NUMERIC(12, 3) NOT NULL DEFAULT 0,
    average_cost      NUMERIC(12, 2),
    last_movement_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_item_store UNIQUE (item_id, store_id)
);

CREATE INDEX idx_stock_store ON stock (store_id);

-- The ledger. Movements are append-only: a correction is another movement, never an edit of
-- an old one, so the running balance can be rebuilt from the beginning at any time.
CREATE TABLE stock_movements (
    id                UUID PRIMARY KEY,
    item_id           UUID         NOT NULL REFERENCES items (id),
    store_id          UUID         NOT NULL REFERENCES stores (id),
    movement_type     VARCHAR(20)   NOT NULL,
    quantity          NUMERIC(12, 3) NOT NULL,
    balance_after     NUMERIC(12, 3),
    unit_cost         NUMERIC(12, 2),
    reference_type    VARCHAR(60),
    reference_id      UUID,
    reason            VARCHAR(400),
    moved_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    moved_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_movements_type CHECK (
        movement_type IN ('PURCHASE', 'RECEIPT', 'ISSUE', 'RETURN', 'TRANSFER_IN',
                          'TRANSFER_OUT', 'ADJUSTMENT', 'WASTAGE')
    ),
    -- A negative movement is written with its own type rather than a minus sign, so a ledger
    -- row is never ambiguous about direction.
    CONSTRAINT ck_stock_movements_sign CHECK (
        (movement_type IN ('ISSUE', 'TRANSFER_OUT', 'WASTAGE') AND quantity > 0)
        OR (movement_type IN ('PURCHASE', 'RECEIPT', 'RETURN', 'TRANSFER_IN') AND quantity > 0)
        OR (movement_type = 'ADJUSTMENT')
    )
);

CREATE INDEX idx_stock_movements_item ON stock_movements (item_id, moved_at DESC);

CREATE INDEX idx_stock_movements_store ON stock_movements (store_id, moved_at DESC);

CREATE INDEX idx_stock_movements_reference ON stock_movements (reference_type, reference_id);

CREATE TABLE purchases (
    id                UUID PRIMARY KEY,
    purchase_number   VARCHAR(40)  NOT NULL,
    supplier_name     VARCHAR(200) NOT NULL,
    supplier_contact  VARCHAR(120),
    store_id          UUID         NOT NULL REFERENCES stores (id),
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    ordered_on        DATE         NOT NULL DEFAULT CURRENT_DATE,
    expected_on       DATE,
    received_on       DATE,
    subtotal          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    other_costs       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchases_number UNIQUE (purchase_number),
    CONSTRAINT ck_purchases_status
        CHECK (status IN ('DRAFT', 'ORDERED', 'PARTIAL', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT ck_purchases_amounts CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND other_costs >= 0 AND total_amount >= 0
    )
);

CREATE INDEX idx_purchases_store ON purchases (store_id, status);

CREATE TABLE purchase_items (
    id                UUID PRIMARY KEY,
    purchase_id       UUID         NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    item_id           UUID         NOT NULL REFERENCES items (id),
    quantity_ordered  NUMERIC(12, 3) NOT NULL,
    quantity_received NUMERIC(12, 3) NOT NULL DEFAULT 0,
    unit_cost         NUMERIC(12, 2) NOT NULL,
    line_total        NUMERIC(14, 2) NOT NULL,
    received_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_items_ordered CHECK (quantity_ordered > 0),
    CONSTRAINT ck_purchase_items_received CHECK (
        quantity_received >= 0 AND quantity_received <= quantity_ordered
    ),
    CONSTRAINT ck_purchase_items_cost CHECK (unit_cost >= 0 AND line_total >= 0),
    CONSTRAINT uk_purchase_items_line UNIQUE (purchase_id, item_id)
);

-- A stock issue is a hand-out against a document: a work order, a department, a course, a
-- person. The document type is named rather than an entity referenced, because the things
-- that consume stock are spread across every module and none of them should depend on the
-- store.
CREATE TABLE stock_issues (
    id                UUID PRIMARY KEY,
    issue_number      VARCHAR(40)  NOT NULL,
    item_id           UUID         NOT NULL REFERENCES items (id),
    store_id          UUID         NOT NULL REFERENCES stores (id),
    quantity          NUMERIC(12, 3) NOT NULL,
    issued_to_type    VARCHAR(40)  NOT NULL DEFAULT 'DEPARTMENT',
    issued_to_id      UUID,
    issued_to_name    VARCHAR(200),
    reason            VARCHAR(400),
    status            VARCHAR(20)   NOT NULL DEFAULT 'ISSUED',
    issued_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    returned_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_issues_number UNIQUE (issue_number),
    CONSTRAINT ck_stock_issues_status CHECK (status IN ('ISSUED', 'RETURNED', 'CANCELLED')),
    CONSTRAINT ck_stock_issues_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_issues_item ON stock_issues (item_id, status);

CREATE TABLE stock_transfers (
    id                UUID PRIMARY KEY,
    transfer_number   VARCHAR(40)  NOT NULL,
    item_id           UUID         NOT NULL REFERENCES items (id),
    from_store_id     UUID         NOT NULL REFERENCES stores (id),
    to_store_id       UUID         NOT NULL REFERENCES stores (id),
    quantity          NUMERIC(12, 3) NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED',
    reason            VARCHAR(400),
    requested_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    dispatched_at     TIMESTAMPTZ,
    received_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_transfers_number UNIQUE (transfer_number),
    CONSTRAINT ck_stock_transfers_stores CHECK (from_store_id <> to_store_id),
    CONSTRAINT ck_stock_transfers_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_transfers_status
        CHECK (status IN ('REQUESTED', 'DISPATCHED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_stock_transfers_item ON stock_transfers (item_id, status);

-- ============================================================================ assets

CREATE TABLE asset_categories (
    id                UUID PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    depreciation_rate NUMERIC(6, 4),
    useful_life_years INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_asset_categories_code UNIQUE (code),
    CONSTRAINT ck_asset_categories_depreciation
        CHECK (depreciation_rate IS NULL OR (depreciation_rate >= 0 AND depreciation_rate <= 1))
);

CREATE TABLE assets (
    id                 UUID PRIMARY KEY,
    asset_number       VARCHAR(40)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    description        VARCHAR(500),
    category_id        UUID REFERENCES asset_categories (id),
    serial_number      VARCHAR(120),
    brand              VARCHAR(120),
    model              VARCHAR(120),
    purchase_date      DATE,
    purchase_cost      NUMERIC(14, 2),
    warranty_expiry    DATE,
    location           VARCHAR(200),
    department_id      UUID REFERENCES departments (id),
    assigned_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    assigned_employee_id UUID REFERENCES employees (id) ON DELETE SET NULL,
    assigned_student_id  UUID REFERENCES students (id) ON DELETE SET NULL,
    assigned_at        TIMESTAMPTZ,
    condition_status   VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    status             VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    notes              VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_assets_number UNIQUE (asset_number),
    CONSTRAINT ck_assets_status CHECK (
        status IN ('AVAILABLE', 'ASSIGNED', 'IN_MAINTENANCE', 'LOST', 'DISPOSED')
    ),
    CONSTRAINT ck_assets_condition CHECK (
        condition_status IN ('NEW', 'GOOD', 'FAIR', 'POOR')
    ),
    CONSTRAINT ck_assets_cost CHECK (purchase_cost IS NULL OR purchase_cost >= 0),
    -- Assigned means assigned. A row claiming a holder while sitting in AVAILABLE is how a
    -- register stops agreeing with the shelf.
    CONSTRAINT ck_assets_assignment CHECK (
        (status = 'ASSIGNED') =
        (num_nonnulls(assigned_user_id, assigned_employee_id, assigned_student_id) = 1)
    )
);

CREATE UNIQUE INDEX uk_assets_serial
    ON assets (serial_number)
    WHERE serial_number IS NOT NULL;

CREATE INDEX idx_assets_status ON assets (status);

CREATE INDEX idx_assets_holder ON assets (assigned_employee_id, status);

-- Who has it now, and who had it before. The answer to "who lost the projector" lives here.
CREATE TABLE asset_assignments (
    id                 UUID PRIMARY KEY,
    asset_id           UUID        NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    holder_type        VARCHAR(20)  NOT NULL,
    holder_user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    holder_employee_id UUID REFERENCES employees (id) ON DELETE SET NULL,
    holder_student_id  UUID REFERENCES students (id) ON DELETE SET NULL,
    holder_name        VARCHAR(200) NOT NULL,
    assigned_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    assigned_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    returned_at        TIMESTAMPTZ,
    condition_out      VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    condition_in       VARCHAR(20),
    notes              VARCHAR(400),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_asset_assignments_holder CHECK (
        num_nonnulls(holder_user_id, holder_employee_id, holder_student_id) <= 1
    )
);

CREATE INDEX idx_asset_assignments_asset ON asset_assignments (asset_id, assigned_at DESC);

-- An asset out on loan is out on loan; a returned one is not.
CREATE UNIQUE INDEX uk_asset_assignments_current
    ON asset_assignments (asset_id)
    WHERE returned_at IS NULL;

CREATE TABLE asset_maintenance (
    id                 UUID PRIMARY KEY,
    asset_id           UUID        NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    maintenance_type   VARCHAR(20)  NOT NULL,
    description        VARCHAR(500),
    vendor             VARCHAR(200),
    cost               NUMERIC(14, 2),
    performed_by       VARCHAR(200),
    scheduled_for      DATE,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    status             VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_asset_maintenance_type CHECK (
        maintenance_type IN ('PREVENTIVE', 'REPAIR', 'CALIBRATION', 'INSPECTION', 'UPGRADE')
    ),
    CONSTRAINT ck_asset_maintenance_status
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_asset_maintenance_cost CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT ck_asset_maintenance_dates
        CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_asset_maintenance_asset ON asset_maintenance (asset_id, status);

-- ======================================================================= documents

-- A document is a file with a name, a history and an owner. The bytes are not here: only the
-- storage key that says where they live and a checksum that proves they arrived intact.
CREATE TABLE documents (
    id                 UUID PRIMARY KEY,
    document_number    VARCHAR(40)  NOT NULL,
    title              VARCHAR(300) NOT NULL,
    description        VARCHAR(1000),
    document_type      VARCHAR(60)  NOT NULL,
    category           VARCHAR(120),
    owner_user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    owner_department_id UUID REFERENCES departments (id),
    related_type       VARCHAR(60),
    related_id         UUID,
    storage_provider   VARCHAR(30)  NOT NULL DEFAULT 'LOCAL',
    storage_key        VARCHAR(500) NOT NULL,
    original_filename  VARCHAR(300) NOT NULL,
    content_type       VARCHAR(120) NOT NULL,
    file_size          BIGINT       NOT NULL,
    checksum_sha256    VARCHAR(64)  NOT NULL,
    page_count         INTEGER,
    current_version    INTEGER       NOT NULL DEFAULT 1,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    verified_at        TIMESTAMPTZ,
    issued_on          DATE,
    expires_on         DATE,
    retention_until    DATE,
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_documents_number UNIQUE (document_number),
    CONSTRAINT ck_documents_version CHECK (current_version >= 1),
    CONSTRAINT ck_documents_size CHECK (file_size >= 0),
    CONSTRAINT ck_documents_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'ARCHIVED')
    ),
    CONSTRAINT ck_documents_verification CHECK (
        verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT ck_documents_expiry CHECK (
        expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on
    )
);

CREATE INDEX idx_documents_related ON documents (related_type, related_id);

CREATE INDEX idx_documents_owner ON documents (owner_user_id);

CREATE INDEX idx_documents_expiry ON documents (expires_on) WHERE expires_on IS NOT NULL;

-- Deleted is not erased: a soft-deleted document keeps its row so the deletion itself is
-- auditable, and the storage bytes are removed separately once the retention window passes.
CREATE INDEX idx_documents_deleted ON documents (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE document_versions (
    id                 UUID PRIMARY KEY,
    document_id        UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    version_number     INTEGER     NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    file_size          BIGINT      NOT NULL,
    checksum_sha256    VARCHAR(64) NOT NULL,
    original_filename  VARCHAR(300) NOT NULL,
    content_type       VARCHAR(120) NOT NULL,
    change_note        VARCHAR(500),
    uploaded_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_versions UNIQUE (document_id, version_number),
    CONSTRAINT ck_document_versions_number CHECK (version_number >= 1),
    CONSTRAINT ck_document_versions_size CHECK (file_size >= 0)
);

-- The head of the chain is documents.current_version, and only that version may be archived:
-- keeping exactly one live version is what the version service checks when it retires one.
CREATE INDEX idx_document_versions_document
    ON document_versions (document_id, version_number DESC);

CREATE TABLE document_access (
    id                 UUID PRIMARY KEY,
    document_id        UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    principal_type     VARCHAR(20)  NOT NULL,
    principal_user_id  UUID REFERENCES users (id) ON DELETE CASCADE,
    principal_role     VARCHAR(40),
    access_level       VARCHAR(20)  NOT NULL DEFAULT 'VIEW',
    granted_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    granted_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_access_level CHECK (access_level IN ('VIEW', 'EDIT', 'MANAGE')),
    CONSTRAINT ck_document_access_principal CHECK (
        (principal_type = 'USER' AND principal_user_id IS NOT NULL AND principal_role IS NULL)
        OR (principal_type = 'ROLE' AND principal_role IS NOT NULL AND principal_user_id IS NULL)
    )
);

CREATE INDEX idx_document_access_document ON document_access (document_id);

CREATE UNIQUE INDEX uk_document_access_user
    ON document_access (document_id, principal_user_id)
    WHERE principal_type = 'USER';

CREATE UNIQUE INDEX uk_document_access_role
    ON document_access (document_id, principal_role)
    WHERE principal_type = 'ROLE';