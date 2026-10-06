package com.educationerp.auth.role;

import com.educationerp.auth.permission.Permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * Default permission sets for built-in roles. These are copied into the database during
 * setup so an institution can adjust them afterwards without touching source code.
 */
public final class RoleDefaults {

    private RoleDefaults() {
    }

    private static final Set<Permission> EVERYTHING = EnumSet.allOf(Permission.class);

    public static Set<Permission> forRole(Role role) {
        return switch (role) {
            case SUPER_ADMIN -> EnumSet.copyOf(EVERYTHING);
            case INSTITUTION_ADMIN -> EnumSet.copyOf(EVERYTHING);
            case PRINCIPAL -> EnumSet.copyOf(readEverything());
            case VICE_PRINCIPAL -> EnumSet.copyOf(readEverything());
            case HOD -> EnumSet.copyOf(readEverything());
            case TEACHER -> teacher();
            case ACCOUNTANT -> accountant();
            case HR -> hr();
            case LIBRARIAN -> librarian();
            case RECEPTIONIST -> receptionist();
            case EXAM_CONTROLLER -> examController();
            case STUDENT -> student();
            case PARENT -> parent();
            case STAFF -> staff();
        };
    }

    private static EnumSet<Permission> readEverything() {
        EnumSet<Permission> set = EnumSet.of(
                Permission.INSTITUTION_READ, Permission.SETTINGS_READ, Permission.AUDIT_READ,
                Permission.USER_READ, Permission.ACADEMIC_READ, Permission.CURRICULUM_READ,
                Permission.TIMETABLE_READ, Permission.CALENDAR_READ,
                Permission.ADMISSION_READ, Permission.STUDENT_READ, Permission.ENROLLMENT_READ,
                Permission.GUARDIAN_READ, Permission.GRADUATION_READ,
                Permission.ATTENDANCE_READ, Permission.ATTENDANCE_APPROVE,
                Permission.EXAM_READ, Permission.RESULT_READ, Permission.REPORT_CARD_READ,
                Permission.TRANSCRIPT_READ, Permission.CERTIFICATE_READ, Permission.GRADING_READ,
                Permission.FEE_READ, Permission.INVOICE_READ, Permission.PAYMENT_READ,
                Permission.DISCOUNT_READ, Permission.SCHOLARSHIP_READ, Permission.FINANCE_REPORT_READ,
                Permission.ACCOUNTING_READ, Permission.LEDGER_READ,
                Permission.EMPLOYEE_READ, Permission.LEAVE_READ, Permission.PAYROLL_READ,
                Permission.PAYSLIP_READ, Permission.ATTENDANCE_EMPLOYEE_READ,
                Permission.LIBRARY_READ, Permission.INVENTORY_READ, Permission.ASSET_READ,
                Permission.DOCUMENT_READ, Permission.COMMUNICATION_READ, Permission.TEMPLATE_READ,
                Permission.REPORT_READ, Permission.REPORT_EXPORT, Permission.IMPORT_READ,
                Permission.SEARCH_GLOBAL, Permission.DOCUMENT_UPLOAD);
        set.add(Permission.DASHBOARD_ADMIN);
        set.add(Permission.DASHBOARD_PRINCIPAL);
        return set;
    }

    private static EnumSet<Permission> teacher() {
        EnumSet<Permission> set = EnumSet.of(
                Permission.INSTITUTION_READ,
                Permission.ACADEMIC_READ, Permission.CURRICULUM_READ,
                Permission.TIMETABLE_READ, Permission.CALENDAR_READ,
                Permission.STUDENT_READ, Permission.ENROLLMENT_READ,
                Permission.ATTENDANCE_READ, Permission.ATTENDANCE_MARK, Permission.ATTENDANCE_CORRECT,
                Permission.EXAM_READ, Permission.MARKS_ENTER,
                Permission.RESULT_READ, Permission.REPORT_CARD_READ,
                Permission.COMMUNICATION_READ, Permission.DOCUMENT_READ, Permission.DOCUMENT_UPLOAD,
                Permission.LEAVE_APPLY, Permission.SEARCH_GLOBAL,
                Permission.DASHBOARD_TEACHER);
        return set;
    }

    private static EnumSet<Permission> accountant() {
        EnumSet<Permission> set = EnumSet.of(
                Permission.INSTITUTION_READ, Permission.ACADEMIC_READ,
                Permission.STUDENT_READ,
                Permission.FEE_READ, Permission.FEE_CREATE, Permission.FEE_APPROVE,
                Permission.INVOICE_READ, Permission.INVOICE_CREATE,
                Permission.PAYMENT_READ, Permission.PAYMENT_CREATE, Permission.PAYMENT_REFUND,
                Permission.DISCOUNT_READ, Permission.DISCOUNT_MANAGE,
                Permission.SCHOLARSHIP_READ, Permission.SCHOLARSHIP_MANAGE,
                Permission.FINANCE_REPORT_READ,
                Permission.ACCOUNTING_READ, Permission.ACCOUNTING_POST, Permission.LEDGER_READ,
                Permission.RECONCILIATION_MANAGE,
                Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.COMMUNICATION_READ, Permission.SEARCH_GLOBAL,
                Permission.DASHBOARD_ACCOUNTANT);
        return set;
    }

    private static EnumSet<Permission> hr() {
        EnumSet<Permission> set = EnumSet.of(
                Permission.INSTITUTION_READ, Permission.ACADEMIC_READ,
                Permission.EMPLOYEE_READ, Permission.EMPLOYEE_CREATE, Permission.EMPLOYEE_UPDATE,
                Permission.LEAVE_READ, Permission.LEAVE_APPROVE, Permission.LEAVE_APPLY,
                Permission.ATTENDANCE_EMPLOYEE_READ, Permission.ATTENDANCE_EMPLOYEE_MARK,
                Permission.PAYROLL_READ, Permission.PAYROLL_PROCESS, Permission.PAYSLIP_READ,
                Permission.SALARY_MANAGE,
                Permission.DOCUMENT_READ, Permission.DOCUMENT_UPLOAD,
                Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.COMMUNICATION_READ, Permission.SEARCH_GLOBAL);
        return set;
    }

    private static EnumSet<Permission> librarian() {
        return EnumSet.of(
                Permission.INSTITUTION_READ,
                Permission.LIBRARY_READ, Permission.LIBRARY_MANAGE, Permission.LIBRARY_CIRCULATE,
                Permission.STUDENT_READ,
                Permission.DOCUMENT_READ, Permission.DOCUMENT_UPLOAD,
                Permission.REPORT_READ, Permission.SEARCH_GLOBAL,
                Permission.COMMUNICATION_READ);
    }

    private static EnumSet<Permission> receptionist() {
        return EnumSet.of(
                Permission.INSTITUTION_READ,
                Permission.STUDENT_READ, Permission.STUDENT_CREATE, Permission.STUDENT_UPDATE,
                Permission.ADMISSION_READ, Permission.ADMISSION_CREATE,
                Permission.GUARDIAN_READ, Permission.GUARDIAN_MANAGE,
                Permission.PAYMENT_READ, Permission.INVOICE_READ,
                Permission.COMMUNICATION_READ, Permission.DOCUMENT_READ, Permission.DOCUMENT_UPLOAD,
                Permission.SEARCH_GLOBAL);
    }

    private static EnumSet<Permission> examController() {
        return EnumSet.of(
                Permission.INSTITUTION_READ, Permission.ACADEMIC_READ,
                Permission.EXAM_READ, Permission.EXAM_CREATE, Permission.EXAM_SCHEDULE_MANAGE,
                Permission.EXAM_ELIGIBILITY_MANAGE,
                Permission.MARKS_VERIFY, Permission.MARKS_APPROVE,
                Permission.RESULT_READ, Permission.RESULT_PUBLISH, Permission.RESULT_CORRECT,
                Permission.REPORT_CARD_GENERATE, Permission.TRANSCRIPT_GENERATE, Permission.CERTIFICATE_GENERATE,
                Permission.GRADING_READ, Permission.GRADING_MANAGE,
                Permission.STUDENT_READ, Permission.ENROLLMENT_READ,
                Permission.REPORT_READ, Permission.REPORT_EXPORT, Permission.SEARCH_GLOBAL,
                Permission.DOCUMENT_READ);
    }

    private static EnumSet<Permission> student() {
        return EnumSet.of(
                Permission.SELF_PROFILE_UPDATE,
                Permission.PORTAL_STUDENT,
                Permission.DASHBOARD_STUDENT);
    }

    private static EnumSet<Permission> parent() {
        return EnumSet.of(
                Permission.SELF_PROFILE_UPDATE,
                Permission.PORTAL_PARENT,
                Permission.DASHBOARD_PARENT);
    }

    private static EnumSet<Permission> staff() {
        return EnumSet.of(
                Permission.INSTITUTION_READ,
                Permission.STUDENT_READ, Permission.ACADEMIC_READ,
                Permission.TIMETABLE_READ, Permission.CALENDAR_READ,
                Permission.ATTENDANCE_READ, Permission.COMMUNICATION_READ,
                Permission.DOCUMENT_READ, Permission.SELF_PROFILE_UPDATE,
                Permission.SEARCH_GLOBAL);
    }
}
