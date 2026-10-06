-- Academic calendar events: term dates, registration windows, exam periods,
-- holidays and fee deadlines. Workflows read these dates instead of hardcoding them.

CREATE TABLE academic_calendar (
    id               UUID PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    academic_year_id UUID         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    semester_id      UUID REFERENCES semesters (id) ON DELETE CASCADE,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    is_working_day   BOOLEAN      NOT NULL DEFAULT TRUE,
    description      VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_academic_calendar_range CHECK (end_date >= start_date)
);

CREATE INDEX idx_academic_calendar_year ON academic_calendar (academic_year_id);
CREATE INDEX idx_academic_calendar_type ON academic_calendar (event_type, start_date);
CREATE INDEX idx_academic_calendar_semester ON academic_calendar (semester_id);
