-- Communication: the notices an institution publishes, the templates that shape a message,
-- and the notification each person ends up reading in their portal.
--
-- Delivery beyond the portal (email, SMS, push, WhatsApp) is not attempted here. A row is
-- written per recipient per channel with its status, so a real provider can pick up work
-- later without this schema having to change.

CREATE TABLE notification_templates (
    id                UUID PRIMARY KEY,
    code              VARCHAR(60)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    channel           VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    event_code        VARCHAR(60)  NOT NULL,
    subject_template  VARCHAR(200) NOT NULL,
    body_template     TEXT         NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_templates_code UNIQUE (code),
    CONSTRAINT ck_notification_templates_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WHATSAPP'))
);

-- One live template per event per channel. A second one is refused at configuration time
-- rather than chosen between at send time.
CREATE UNIQUE INDEX uk_notification_templates_event_channel
    ON notification_templates (event_code, channel);

CREATE TABLE notices (
    id                UUID PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    body              TEXT         NOT NULL,
    audience          VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    published_by      UUID,
    expires_at        TIMESTAMPTZ,
    recipient_count   INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_notices_audience
        CHECK (audience IN ('ALL', 'STUDENTS', 'PARENTS', 'TEACHERS', 'STAFF')),
    CONSTRAINT ck_notices_status
        CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_notices_status_published ON notices (status, published_at DESC);

CREATE TABLE notifications (
    id                UUID PRIMARY KEY,
    recipient_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel           VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    event_code        VARCHAR(60) NOT NULL,
    subject           VARCHAR(200) NOT NULL,
    body              TEXT        NOT NULL,
    priority          VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    status            VARCHAR(10) NOT NULL DEFAULT 'SENT',
    related_type      VARCHAR(60),
    related_id        UUID,
    notice_id         UUID REFERENCES notices (id) ON DELETE CASCADE,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_notifications_status CHECK (status IN ('SENT', 'READ')),
    CONSTRAINT ck_notifications_priority CHECK (priority IN ('NORMAL', 'HIGH'))
);

CREATE INDEX idx_notifications_recipient
    ON notifications (recipient_user_id, sent_at DESC);

CREATE INDEX idx_notifications_unread
    ON notifications (recipient_user_id)
    WHERE read_at IS NULL;

-- A person is told once about a given thing. Re-marking an absence or replaying a payment
-- callback must not produce a second identical notification.
CREATE UNIQUE INDEX uk_notifications_once_per_thing
    ON notifications (recipient_user_id, event_code, related_type, related_id)
    WHERE related_id IS NOT NULL;

-- A guardian is a person, and a person signs in. Without this column a parent account has no
-- way to prove which children are theirs, which is exactly what the parent portal has to
-- check on every request.
ALTER TABLE guardians ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_guardians_user
    ON guardians (user_id)
    WHERE user_id IS NOT NULL;

-- Starter templates. They exist so a new institution can send a sensible message without
-- writing one first, and every one of them is editable from the settings screen.
INSERT INTO notification_templates
    (id, code, name, channel, event_code, subject_template, body_template, is_active)
VALUES
    (gen_random_uuid(), 'attendance-absent',
     'Student marked absent', 'IN_APP', 'AttendanceMarkedAbsent',
     'Attendance recorded for {{studentName}}',
     E'Dear {{studentName}},\n\nYour absence on {{date}} has been recorded by {{markedBy}}.\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'fee-due',
     'Fee due reminder', 'IN_APP', 'FeeDue',
     'Fee due: {{amount}}',
     E'Dear {{studentName}},\n\n{{amount}} is due on {{dueDate}} for {{feeName}}.\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'payment-completed',
     'Payment received', 'IN_APP', 'PaymentCompleted',
     'Payment received',
     E'Dear {{studentName}},\n\nYour payment of NPR {{amount}} has been received.\n\nReceipt: {{receiptNumber}}\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'result-published',
     'Result published', 'IN_APP', 'ResultPublished',
     'Result published for {{examName}}',
     E'Dear {{studentName}},\n\nYour result for {{examName}} has been published. Total: {{total}}, grade: {{grade}}.\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'admission-approved',
     'Admission approved', 'IN_APP', 'AdmissionApproved',
     'Your admission has been approved',
     E'Dear {{studentName}},\n\nYour admission for {{programName}} has been approved. Student number: {{studentNumber}}.\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'leave-approved',
     'Leave approved', 'IN_APP', 'LeaveApproved',
     'Your leave request has been approved',
     E'Dear {{employeeName}},\n\nYour {{leaveType}} leave from {{startDate}} to {{endDate}} has been approved.\n\n{{institution.name}}',
     TRUE),
    (gen_random_uuid(), 'notice-published',
     'Notice published', 'IN_APP', 'NoticePublished',
     '{{noticeTitle}}',
     '{{noticeBody}}',
     TRUE);