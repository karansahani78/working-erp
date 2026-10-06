package com.educationerp.reporting;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * One report: what it is called, who may run it, what it needs, and how to build it.
 *
 * <p>Definitions are data rather than methods, which is what lets the catalogue be listed in one
 * endpoint and every report share the same permission, date-range and export handling. A report
 * that forgets to check a permission cannot exist here, because the permission is not optional.
 */
public record ReportDefinition(
        String key,
        String title,
        String group,
        String description,
        String permission,
        Set<String> parameters,
        Function<ReportContext, ReportTable> build) {

    public ReportDefinition {
        parameters = parameters == null ? Set.of() : Set.copyOf(parameters);
    }

    /** Mark a report as accepting any of the standard period parameters. */
    public boolean takesDates() {
        return parameters.contains("from") && parameters.contains("to");
    }

    public List<String> parameterList() {
        return parameters.stream().sorted().toList();
    }
}
