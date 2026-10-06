-- Core foundation: institution, branding, modules, users, roles, sessions, audit.
-- No demo data: an empty database must remain empty until the setup wizard runs.

CREATE TABLE institution (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    short_name            VARCHAR(40)  NOT NULL,
    institution_code      VARCHAR(40)  NOT NULL,
    institution_type      VARCHAR(40)  NOT NULL,
    logo_url              VARCHAR(500),
    favicon_url           VARCHAR(500),
    primary_color         VARCHAR(20)  NOT NULL DEFAULT '#0F5132',
    secondary_color       VARCHAR(20)  NOT NULL DEFAULT '#EAE5DB',
    address               VARCHAR(400),
    municipality          VARCHAR(120),
    district              VARCHAR(120),
    province              VARCHAR(120),
    country               VARCHAR(80)  NOT NULL DEFAULT 'Nepal',
    phone                 VARCHAR(60),
    email                 VARCHAR(180),
    website               VARCHAR(200),
    timezone              VARCHAR(80)  NOT NULL DEFAULT 'Asia/Kathmandu',
    currency              VARCHAR(10)  NOT NULL DEFAULT 'NPR',
    fiscal_year_start     DATE         NOT NULL,
    date_format           VARCHAR(20)  NOT NULL DEFAULT 'AD',
    academic_model        VARCHAR(20)  NOT NULL,
    portal_title          VARCHAR(120),
    portal_description    VARCHAR(400),
    support_email         VARCHAR(180),
    support_phone         VARCHAR(60),
    setup_completed       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_institution_code UNIQUE (institution_code),
    CONSTRAINT ck_institution_date_format CHECK (date_format IN ('AD', 'BS'))
);

CREATE TABLE module_settings (
    id          UUID PRIMARY KEY,
    module_key  VARCHAR(40) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    settings    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_module_settings_key UNIQUE (module_key)
);

-- ---------------------------------------------------------------- identity & access

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    built_in    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id    UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    username              VARCHAR(80)  NOT NULL,
    email                 VARCHAR(180),
    phone                 VARCHAR(40),
    password_hash         VARCHAR(200) NOT NULL,
    display_name          VARCHAR(150) NOT NULL,
    primary_role          VARCHAR(40)  NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    employee_id           UUID,
    student_id            UUID,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ,
    failed_login_count    INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_failed_login_at  TIMESTAMPTZ,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_ip       VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE INDEX idx_users_email ON users (lower(email));
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_student ON users (student_id);
CREATE INDEX idx_users_employee ON users (employee_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at     TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    rotated_to_id UUID,
    client_ip     VARCHAR(64),
    user_agent    VARCHAR(400),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL,
    purpose       VARCHAR(30) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    requested_ip  VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash)
);

CREATE INDEX idx_pw_reset_user ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------------- audit trail

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY,
    actor_id       UUID,
    actor_username VARCHAR(150),
    action         VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(80)  NOT NULL,
    entity_id      VARCHAR(64),
    entity_label   VARCHAR(255),
    summary        VARCHAR(500),
    before_state   JSONB,
    after_state    JSONB,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(400),
    request_id     VARCHAR(64),
    succeeded      BOOLEAN      NOT NULL DEFAULT TRUE,
    failure_reason VARCHAR(500),
    module         VARCHAR(40),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
