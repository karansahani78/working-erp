package com.educationerp.academic;

/**
 * The label an offering is known by in timetables, results and reports.
 *
 * <p>It is built from the group the offering is taught to, which is a section in a school
 * and a programme or term in a college, so the same string is meaningful in either model.
 */
final class OfferingCodes {

    private OfferingCodes() {
    }

    static String of(CourseOffering offering) {
        String group = offering.getSection() != null ? offering.getSection().getCode()
                : offering.getProgram() != null ? offering.getProgram().getCode()
                : offering.getSemester() != null ? offering.getSemester().getName() : "ALL";
        return offering.getCourse().getCode() + "-" + group + "-" + offering.getAcademicYear().getCode();
    }
}
