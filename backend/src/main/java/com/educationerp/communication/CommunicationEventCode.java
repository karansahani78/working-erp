package com.educationerp.communication;

/**
 * The events a message can be raised for. Each one has a template that decides how it reads,
 * so the wording of a receipt or an absence notice is changed in configuration rather than in
 * the code that raises it.
 */
public enum CommunicationEventCode {
    ATTENDANCE_MARKED_ABSENT("AttendanceMarkedAbsent"),
    FEE_DUE("FeeDue"),
    PAYMENT_COMPLETED("PaymentCompleted"),
    RESULT_PUBLISHED("ResultPublished"),
    ADMISSION_APPROVED("AdmissionApproved"),
    LEAVE_APPROVED("LeaveApproved"),
    NOTICE_PUBLISHED("NoticePublished");

    private final String code;

    CommunicationEventCode(String code) {
        this.code = code;
    }

    /** The stable name stored in the template and the notification. */
    public String code() {
        return code;
    }
}
