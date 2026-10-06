package com.educationerp.institution;

/**
 * Optional modules. A disabled module is hidden in navigation, blocked at the API and
 * refused on direct route access — never merely hidden in the UI.
 */
public enum ModuleKey {

    ADMISSIONS(true),
    STUDENTS(true),
    ATTENDANCE(true),
    EXAMINATION(true),
    FINANCE(true),
    ACCOUNTING(false),
    HR(false),
    PAYROLL(false),
    LIBRARY(false),
    INVENTORY(false),
    ASSETS(false),
    DOCUMENTS(true),
    COMMUNICATION(true),
    REPORTS(true),
    IMPORTS(true),
    AUDIT(true),
    ALUMNI(false),
    HOSTEL(false),
    TRANSPORT(false),
    PLACEMENT(false),
    RESEARCH(false),
    ONLINE_EXAM(false);

    private final boolean enabledByDefault;

    ModuleKey(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }
}
