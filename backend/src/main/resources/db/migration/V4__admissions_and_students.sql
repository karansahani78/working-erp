-- Admissions and Students: campaigns, applications, application documents,
-- admission decisions, students, guardians, guardian relationships and enrolment.

CREATE TABLE admission_campaigns (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(40)  NOT NULL,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    open_date          DATE,
    close_date         DATE,
    application_fee    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    capacity           INTEGER,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    document_requirements JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_admission_campaigns_code UNIQUE (code)
);

CREATE INDEX idx_admission_campaigns_year ON admission_campaigns (academic_year_id);

CREATE TABLE admission_applications (
    id                 UUID PRIMARY KEY,
    campaign_id        UUID NOT NULL REFERENCES admission_campaigns (id) ON DELETE CASCADE,
    reference_code     VARCHAR(40) NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    middle_name        VARCHAR(100),
    last_name          VARCHAR(100),
    date_of_birth      DATE,
    gender             VARCHAR(20),
    nationality        VARCHAR(80),
    phone              VARCHAR(60),
    email              VARCHAR(180),
    address            VARCHAR(400),
    photo_url          VARCHAR(400),
    applying_program_id UUID REFERENCES programs (id) ON DELETE SET NULL,
    applying_class_id  UUID REFERENCES school_classes (id) ON DELETE SET NULL,
    previous_school    VARCHAR(200),
    previous_qualification VARCHAR(120),
    previous_percentage NUMERIC(5, 2),
    entrance_score     NUMERIC(7, 2),
    merit_score        NUMERIC(7, 2),
    status             VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_at       DATE,
    decided_at         DATE,
    decision_notes     VARCHAR(1000),
    student_id         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_admission_applications_reference UNIQUE (reference_code)
);

CREATE INDEX idx_admission_applications_campaign ON admission_applications (campaign_id);
CREATE INDEX idx_admission_applications_status ON admission_applications (status);
CREATE INDEX idx_admission_applications_email ON admission_applications (email);

CREATE TABLE application_documents (
    id                 UUID PRIMARY KEY,
    application_id     UUID NOT NULL REFERENCES admission_applications (id) ON DELETE CASCADE,
    document_type      VARCHAR(60)  NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(400) NOT NULL,
    content_type       VARCHAR(120),
    size_bytes         BIGINT,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_notes       VARCHAR(500),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_application_documents_application ON application_documents (application_id);

CREATE TABLE admission_decisions (
    id                 UUID PRIMARY KEY,
    application_id     UUID NOT NULL REFERENCES admission_applications (id) ON DELETE CASCADE,
    decision           VARCHAR(20) NOT NULL,
    decided_by         UUID,
    decided_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes              VARCHAR(1000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_admission_decisions_application ON admission_decisions (application_id);

CREATE TABLE students (
    id                 UUID PRIMARY KEY,
    student_number     VARCHAR(40) NOT NULL,
    user_id            UUID REFERENCES users (id) ON DELETE SET NULL,
    admission_id      UUID,
    first_name         VARCHAR(100) NOT NULL,
    middle_name        VARCHAR(100),
    last_name          VARCHAR(100),
    date_of_birth      DATE,
    gender             VARCHAR(20),
    nationality        VARCHAR(80),
    phone              VARCHAR(60),
    email              VARCHAR(180),
    address            VARCHAR(400),
    photo_url          VARCHAR(400),
    enrollment_date    DATE,
    status             VARCHAR(20) NOT NULL DEFAULT 'APPLICANT',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_students_number UNIQUE (student_number)
);

CREATE INDEX idx_students_status ON students (status);
CREATE INDEX idx_students_email ON students (email);

ALTER TABLE admission_applications
    ADD CONSTRAINT fk_admission_applications_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE SET NULL;

CREATE TABLE guardians (
    id                 UUID PRIMARY KEY,
    first_name         VARCHAR(100) NOT NULL,
    middle_name        VARCHAR(100),
    last_name          VARCHAR(100),
    phone              VARCHAR(60),
    email              VARCHAR(180),
    occupation         VARCHAR(150),
    address            VARCHAR(400),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE student_guardians (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    guardian_id        UUID NOT NULL REFERENCES guardians (id) ON DELETE CASCADE,
    relationship       VARCHAR(30) NOT NULL,
    is_primary         BOOLEAN     NOT NULL DEFAULT FALSE,
    can_pickup        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_student_guardians UNIQUE (student_id, guardian_id, relationship)
);

CREATE INDEX idx_student_guardians_guardian ON student_guardians (guardian_id);

CREATE TABLE enrollments (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    semester_id        UUID REFERENCES semesters (id) ON DELETE SET NULL,
    school_class_id    UUID REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id         UUID REFERENCES sections (id) ON DELETE SET NULL,
    roll_number        VARCHAR(20),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at        DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_enrollments_student_year UNIQUE (student_id, academic_year_id, semester_id, school_class_id)
);

CREATE INDEX idx_enrollments_academic_year ON enrollments (academic_year_id);
CREATE INDEX idx_enrollments_section ON enrollments (section_id);

-- Server-side student number sequences. One row per format/prefix/year combination so
-- numbers stay gap-tolerant without relying on database sequences per institution.
CREATE TABLE student_number_sequences (
    id                 UUID PRIMARY KEY,
    prefix             VARCHAR(20)  NOT NULL,
    period             VARCHAR(10)  NOT NULL,
    current_value      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_student_number_sequences UNIQUE (prefix, period)
);

CREATE TABLE student_number_settings (
    id                 UUID PRIMARY KEY,
    format             VARCHAR(60)  NOT NULL DEFAULT '{PREFIX}-{YEAR}-{SEQ:5}',
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0
);