package com.educationerp.institution;

/**
 * Determines which academic structures the UI renders.
 *
 * SCHOOL   &rarr; Academic Year &gt; Class &gt; Section &gt; Subject
 * COLLEGE  &rarr; Faculty &gt; Department &gt; Program &gt; Semester &gt; Course
 * UNIVERSITY &rarr; Faculty &gt; Department &gt; Program &gt; ProgramVersion &gt; Curriculum &gt; Semester &gt; Course
 */
public enum AcademicModel {
    SCHOOL,
    COLLEGE,
    UNIVERSITY,
    HYBRID;

    public boolean usesClassSectionStructure() {
        return this == SCHOOL || this == HYBRID;
    }

    public boolean usesFacultyDepartmentProgram() {
        return this == COLLEGE || this == UNIVERSITY || this == HYBRID;
    }

    public boolean usesProgramVersions() {
        return this == UNIVERSITY || this == HYBRID;
    }
}
