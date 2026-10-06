-- Attendance and Examination: attendance records with correction/approval workflow,
-- exams, exam schedules, exam subjects, mark entry, institution-configurable grading,
-- results, result corrections, report cards and transcripts.

-- ---------------------------------------------------------------- attendance

CREATE TABLE attendance_records (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    course_offering_id UUID REFERENCES course_offerings (id) ON DELETE CASCADE,
    enrollment_id      UUID REFERENCES enrollments (id) ON DELETE SET NULL,
    attendance_date    DATE NOT NULL,
    time_slot_id       UUID REFERENCES time_slots (id) ON DELETE SET NULL,
    period_type        VARCHAR(20) NOT NULL DEFAULT 'PERIOD',
    status             VARCHAR(20) NOT NULL,
    minutes_late       INTEGER,
    recorded_by        UUID,
    status_workflow    VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    remarks            VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_attendance_records UNIQUE (student_id, course_offering_id, attendance_date, time_slot_id)
);

CREATE INDEX idx_attendance_records_student_date ON attendance_records (student_id, attendance_date);
CREATE INDEX idx_attendance_records_offering ON attendance_records (course_offering_id, attendance_date);
CREATE INDEX idx_attendance_records_workflow ON attendance_records (status_workflow);

ALTER TABLE attendance_records
    ADD CONSTRAINT ck_attendance_minutes_late CHECK (minutes_late IS NULL OR minutes_late >= 0);

CREATE TABLE attendance_corrections (
    id                 UUID PRIMARY KEY,
    attendance_id      UUID NOT NULL REFERENCES attendance_records (id) ON DELETE CASCADE,
    old_status         VARCHAR(20) NOT NULL,
    new_status         VARCHAR(20) NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    requested_by       UUID,
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by        UUID,
    approved_at        TIMESTAMPTZ,
    applied_at         TIMESTAMPTZ,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_notes     VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_attendance_corrections_attendance ON attendance_corrections (attendance_id);

-- ---------------------------------------------------------------- examination

CREATE TABLE examinations (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(40)  NOT NULL,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    semester_id        UUID REFERENCES semesters (id) ON DELETE SET NULL,
    exam_type          VARCHAR(30) NOT NULL DEFAULT 'TERM',
    start_date         DATE,
    end_date           DATE,
    grading_scale_id   UUID,
    max_total_marks    NUMERIC(8, 2),
    pass_percentage    NUMERIC(5, 2),
    status             VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_examinations_code UNIQUE (code)
);

CREATE INDEX idx_examinations_year ON examinations (academic_year_id);

CREATE TABLE exam_subjects (
    id                 UUID PRIMARY KEY,
    examination_id     UUID NOT NULL REFERENCES examinations (id) ON DELETE CASCADE,
    course_offering_id UUID REFERENCES course_offerings (id) ON DELETE SET NULL,
    subject_name       VARCHAR(150) NOT NULL,
    subject_code       VARCHAR(40),
    exam_date          DATE,
    start_time         TIME,
    end_time           TIME,
    max_marks          NUMERIC(8, 2) NOT NULL DEFAULT 100,
    pass_marks         NUMERIC(8, 2),
    room_id            UUID REFERENCES rooms (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_exam_subjects_exam_name UNIQUE (examination_id, subject_name)
);

CREATE INDEX idx_exam_subjects_examination ON exam_subjects (examination_id);

-- Institution-configurable grading. Boundaries are rows rather than hardcoded logic so
-- an institution can define its own scale without a code change.
CREATE TABLE grading_scales (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    code               VARCHAR(30)  NOT NULL,
    description        VARCHAR(300),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_grading_scales_code UNIQUE (code)
);

CREATE TABLE grade_boundaries (
    id                 UUID PRIMARY KEY,
    grading_scale_id   UUID NOT NULL REFERENCES grading_scales (id) ON DELETE CASCADE,
    letter_grade       VARCHAR(10) NOT NULL,
    grade_point        NUMERIC(4, 2) NOT NULL,
    min_percentage     NUMERIC(5, 2) NOT NULL,
    max_percentage     NUMERIC(5, 2),
    pass_flag           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_grade_boundaries_scale_letter UNIQUE (grading_scale_id, letter_grade),
    CONSTRAINT ck_grade_boundaries_range CHECK (max_percentage IS NULL OR max_percentage > min_percentage)
);

CREATE INDEX idx_grade_boundaries_scale ON grade_boundaries (grading_scale_id);

CREATE TABLE results (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    examination_id     UUID NOT NULL REFERENCES examinations (id) ON DELETE CASCADE,
    exam_subject_id    UUID NOT NULL REFERENCES exam_subjects (id) ON DELETE CASCADE,
    enrollment_id      UUID REFERENCES enrollments (id) ON DELETE SET NULL,
    marks_obtained     NUMERIC(8, 2),
    max_marks          NUMERIC(8, 2) NOT NULL,
    percentage         NUMERIC(6, 2),
    letter_grade       VARCHAR(10),
    grade_point        NUMERIC(4, 2),
    is_pass            BOOLEAN,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_results_student_subject UNIQUE (student_id, exam_subject_id),
    CONSTRAINT ck_results_marks_range CHECK (marks_obtained IS NULL OR (marks_obtained >= 0 AND marks_obtained <= max_marks))
);

CREATE INDEX idx_results_examination ON results (examination_id);
CREATE INDEX idx_results_student ON results (student_id);

ALTER TABLE examinations
    ADD CONSTRAINT fk_examinations_grading_scale
        FOREIGN KEY (grading_scale_id) REFERENCES grading_scales (id) ON DELETE SET NULL;

-- Published results are protected; a change goes through a correction request.
CREATE TABLE result_corrections (
    id                 UUID PRIMARY KEY,
    result_id          UUID NOT NULL REFERENCES results (id) ON DELETE CASCADE,
    old_marks          NUMERIC(8, 2),
    new_marks          NUMERIC(8, 2),
    old_grade          VARCHAR(10),
    new_grade          VARCHAR(10),
    reason             VARCHAR(1000) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by       UUID,
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by        UUID,
    approved_at        TIMESTAMPTZ,
    applied_at         TIMESTAMPTZ,
    approval_notes     VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_result_corrections_result ON result_corrections (result_id);

CREATE TABLE report_cards (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    academic_year_id   UUID REFERENCES academic_years (id) ON DELETE SET NULL,
    semester_id        UUID REFERENCES semesters (id) ON DELETE SET NULL,
    enrollment_id      UUID REFERENCES enrollments (id) ON DELETE SET NULL,
    reference_code     VARCHAR(40) NOT NULL,
    total_marks        NUMERIC(10, 2),
    total_credits      NUMERIC(6, 2),
    gpa                NUMERIC(4, 2),
    cgpa               NUMERIC(4, 2),
    attendance_percentage NUMERIC(5, 2),
    overall_result     VARCHAR(30),
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_report_cards_reference UNIQUE (reference_code),
    CONSTRAINT uk_report_cards_student_period UNIQUE (student_id, academic_year_id, semester_id)
);

CREATE INDEX idx_report_cards_student ON report_cards (student_id);

CREATE TABLE report_card_items (
    id                 UUID PRIMARY KEY,
    report_card_id     UUID NOT NULL REFERENCES report_cards (id) ON DELETE CASCADE,
    exam_subject_id    UUID REFERENCES exam_subjects (id) ON DELETE SET NULL,
    subject_name       VARCHAR(150) NOT NULL,
    subject_code       VARCHAR(40),
    marks_obtained     NUMERIC(8, 2),
    max_marks          NUMERIC(8, 2),
    credits            NUMERIC(5, 2),
    letter_grade       VARCHAR(10),
    grade_point        NUMERIC(4, 2),
    is_pass            BOOLEAN,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_report_card_items_card ON report_card_items (report_card_id);

CREATE TABLE transcripts (
    id                 UUID PRIMARY KEY,
    student_id         UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    reference_code     VARCHAR(40) NOT NULL,
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_by       UUID,
    total_credits      NUMERIC(6, 2),
    cumulative_gpa     NUMERIC(4, 2),
    cumulative_cgpa    NUMERIC(4, 2),
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    finalised_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transcripts_reference UNIQUE (reference_code)
);

CREATE INDEX idx_transcripts_student ON transcripts (student_id);