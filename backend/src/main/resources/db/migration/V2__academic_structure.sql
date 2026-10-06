-- Academic structure: campuses, faculties, departments, programmes, curriculum,
-- academic years, semesters, school classes/sections, courses, offerings, rooms,
-- time slots and the weekly timetable.

CREATE TABLE campuses (
    id          UUID PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(400),
    phone       VARCHAR(60),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_campuses_code UNIQUE (code)
);

CREATE TABLE faculties (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    campus_id   UUID REFERENCES campuses (id) ON DELETE SET NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_faculties_code UNIQUE (code)
);

CREATE TABLE departments (
    id               UUID PRIMARY KEY,
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    faculty_id       UUID REFERENCES faculties (id) ON DELETE SET NULL,
    head_employee_id UUID,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_departments_code UNIQUE (code)
);

CREATE INDEX idx_departments_faculty ON departments (faculty_id);

CREATE TABLE programs (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(40)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    level               VARCHAR(40),
    duration_semesters  INTEGER,
    duration_years      INTEGER,
    department_id       UUID REFERENCES departments (id) ON DELETE SET NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_programs_code UNIQUE (code),
    CONSTRAINT ck_programs_duration CHECK (duration_semesters IS NULL OR duration_semesters > 0)
);

CREATE INDEX idx_programs_department ON programs (department_id);

CREATE TABLE academic_years (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(40)  NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    calendar   VARCHAR(10)  NOT NULL DEFAULT 'AD',
    status     VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    is_current BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_academic_years_code UNIQUE (code),
    CONSTRAINT uk_academic_years_name UNIQUE (name),
    CONSTRAINT ck_academic_years_range CHECK (end_date >= start_date),
    CONSTRAINT ck_academic_years_calendar CHECK (calendar IN ('AD', 'BS'))
);

CREATE UNIQUE INDEX uq_academic_years_current ON academic_years (is_current) WHERE is_current;

CREATE TABLE semesters (
    id               UUID PRIMARY KEY,
    academic_year_id UUID         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(80)  NOT NULL,
    ordinal          INTEGER      NOT NULL,
    start_date       DATE,
    end_date         DATE,
    type             VARCHAR(20)  NOT NULL DEFAULT 'SEMESTER',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_semesters_year_ordinal UNIQUE (academic_year_id, ordinal),
    CONSTRAINT ck_semesters_ordinal CHECK (ordinal > 0),
    CONSTRAINT ck_semesters_range CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE TABLE program_versions (
    id              UUID PRIMARY KEY,
    program_id      UUID        NOT NULL REFERENCES programs (id) ON DELETE CASCADE,
    label           VARCHAR(60) NOT NULL,
    effective_from  DATE,
    effective_to    DATE,
    total_credits   INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_program_versions_program_label UNIQUE (program_id, label)
);

CREATE TABLE curricula (
    id                 UUID PRIMARY KEY,
    program_version_id UUID         NOT NULL REFERENCES program_versions (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    description        VARCHAR(500),
    total_credits      INTEGER,
    effective_from     DATE,
    effective_to       DATE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_curricula_version_name UNIQUE (program_version_id, name)
);

CREATE TABLE school_classes (
    id               UUID PRIMARY KEY,
    academic_year_id UUID         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(60)  NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    ordinal          INTEGER,
    grade_level      INTEGER,
    stream           VARCHAR(40),
    capacity         INTEGER,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_school_classes_year_code UNIQUE (academic_year_id, code),
    CONSTRAINT ck_school_classes_capacity CHECK (capacity IS NULL OR capacity >= 0)
);

CREATE TABLE sections (
    id             UUID PRIMARY KEY,
    class_id       UUID         NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    name           VARCHAR(60)  NOT NULL,
    code           VARCHAR(20)  NOT NULL,
    capacity       INTEGER,
    room           VARCHAR(60),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sections_class_code UNIQUE (class_id, code),
    CONSTRAINT ck_sections_capacity CHECK (capacity IS NULL OR capacity >= 0)
);

CREATE TABLE courses (
    id               UUID PRIMARY KEY,
    code             VARCHAR(40)   NOT NULL,
    name             VARCHAR(180)  NOT NULL,
    description      VARCHAR(1000),
    course_type      VARCHAR(20)   NOT NULL DEFAULT 'COURSE',
    department_id    UUID REFERENCES departments (id) ON DELETE SET NULL,
    program_id       UUID REFERENCES programs (id) ON DELETE SET NULL,
    credit_hours     INTEGER,
    weekly_hours     INTEGER,
    lecture_hours    INTEGER,
    practical_hours  INTEGER,
    internal_marks   INTEGER,
    external_marks   INTEGER,
    total_marks      INTEGER,
    is_elective      BOOLEAN       NOT NULL DEFAULT FALSE,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT ck_courses_credit CHECK (credit_hours IS NULL OR credit_hours > 0),
    CONSTRAINT ck_courses_marks CHECK (total_marks IS NULL OR total_marks >= 0)
);

CREATE INDEX idx_courses_department ON courses (department_id);
CREATE INDEX idx_courses_program ON courses (program_id);

CREATE TABLE curriculum_courses (
    id               UUID PRIMARY KEY,
    curriculum_id    UUID        NOT NULL REFERENCES curricula (id) ON DELETE CASCADE,
    semester_id      UUID REFERENCES semesters (id) ON DELETE CASCADE,
    course_id        UUID        NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    requirement_type VARCHAR(20) NOT NULL DEFAULT 'MANDATORY',
    credit_hours     INTEGER,
    internal_marks   INTEGER,
    external_marks   INTEGER,
    elective_group   VARCHAR(60),
    ordinal          INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_curriculum_courses_semester_course UNIQUE (curriculum_id, semester_id, course_id)
);

CREATE INDEX idx_curriculum_courses_curriculum ON curriculum_courses (curriculum_id);
CREATE INDEX idx_curriculum_courses_semester ON curriculum_courses (semester_id);

CREATE TABLE rooms (
    id         UUID PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    building   VARCHAR(120),
    capacity   INTEGER,
    room_type  VARCHAR(40),
    campus_id  UUID REFERENCES campuses (id) ON DELETE SET NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_rooms_code UNIQUE (code),
    CONSTRAINT ck_rooms_capacity CHECK (capacity IS NULL OR capacity >= 0)
);

CREATE TABLE course_offerings (
    id               UUID PRIMARY KEY,
    course_id        UUID         NOT NULL REFERENCES courses (id) ON DELETE RESTRICT,
    academic_year_id UUID         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    semester_id      UUID REFERENCES semesters (id) ON DELETE SET NULL,
    program_id       UUID REFERENCES programs (id) ON DELETE SET NULL,
    curriculum_id    UUID REFERENCES curricula (id) ON DELETE SET NULL,
    school_class_id  UUID REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id       UUID REFERENCES sections (id) ON DELETE SET NULL,
    teacher_id       UUID,
    teacher_name     VARCHAR(150),
    room_id          UUID REFERENCES rooms (id) ON DELETE SET NULL,
    capacity         INTEGER,
    enrolled_count   INTEGER      NOT NULL DEFAULT 0,
    weekly_periods   INTEGER      NOT NULL DEFAULT 0,
    internal_marks   INTEGER,
    external_marks   INTEGER,
    total_marks      INTEGER,
    pass_marks       INTEGER,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_offerings_enrolled CHECK (enrolled_count >= 0),
    CONSTRAINT ck_offerings_marks CHECK (total_marks IS NULL OR total_marks >= 0),
    -- A school offering needs a class; a higher-education offering needs a semester.
    CONSTRAINT ck_offerings_scope CHECK (
        (school_class_id IS NOT NULL AND semester_id IS NULL)
        OR (school_class_id IS NULL AND semester_id IS NOT NULL)
    )
);

CREATE INDEX idx_offerings_year_semester ON course_offerings (academic_year_id, semester_id);
CREATE INDEX idx_offerings_class_section ON course_offerings (school_class_id, section_id);
CREATE INDEX idx_offerings_teacher ON course_offerings (teacher_id);
CREATE INDEX idx_offerings_program ON course_offerings (program_id);
CREATE INDEX idx_offerings_course ON course_offerings (course_id);

CREATE TABLE time_slots (
    id          UUID PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    slot_type   VARCHAR(20) NOT NULL DEFAULT 'LECTURE',
    ordinal     INTEGER     NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_time_slots_name UNIQUE (name),
    CONSTRAINT ck_time_slots_range CHECK (end_time > start_time),
    CONSTRAINT ck_time_slots_ordinal CHECK (ordinal > 0)
);

CREATE TABLE timetable_entries (
    id                 UUID PRIMARY KEY,
    day_of_week        VARCHAR(10) NOT NULL,
    time_slot_id       UUID        NOT NULL REFERENCES time_slots (id) ON DELETE RESTRICT,
    course_offering_id UUID        NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    section_id         UUID REFERENCES sections (id) ON DELETE CASCADE,
    school_class_id    UUID REFERENCES school_classes (id) ON DELETE CASCADE,
    room_id            UUID REFERENCES rooms (id) ON DELETE SET NULL,
    teacher_id         UUID,
    effective_from     DATE,
    effective_to       DATE,
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_timetable_range CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_timetable_day_slot ON timetable_entries (day_of_week, time_slot_id);
CREATE INDEX idx_timetable_section ON timetable_entries (section_id);
CREATE INDEX idx_timetable_teacher ON timetable_entries (teacher_id);
CREATE INDEX idx_timetable_room ON timetable_entries (room_id);
CREATE INDEX idx_timetable_offering ON timetable_entries (course_offering_id);

-- A section (or class without sections) cannot be booked twice in the same slot.
CREATE UNIQUE INDEX uq_timetable_group_slot ON timetable_entries (day_of_week, time_slot_id, section_id)
    WHERE active AND section_id IS NOT NULL;
CREATE UNIQUE INDEX uq_timetable_class_slot ON timetable_entries (day_of_week, time_slot_id, school_class_id)
    WHERE active AND section_id IS NULL AND school_class_id IS NOT NULL;
-- A room cannot host two classes at once.
CREATE UNIQUE INDEX uq_timetable_room_slot ON timetable_entries (day_of_week, time_slot_id, room_id)
    WHERE active AND room_id IS NOT NULL;
-- A teacher cannot be in two places at once.
CREATE UNIQUE INDEX uq_timetable_teacher_slot ON timetable_entries (day_of_week, time_slot_id, teacher_id)
    WHERE active AND teacher_id IS NOT NULL;
