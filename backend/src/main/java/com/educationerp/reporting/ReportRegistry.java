package com.educationerp.reporting;

import com.educationerp.common.error.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every report the installation can produce.
 *
 * <p>Built once at startup from a list of definitions. Each one names the permission that runs
 * it, so an unauthorised report is not reachable by knowing its key, and the catalogue endpoint
 * lists what a caller may actually run rather than what exists.
 */
public final class ReportRegistry {

    private static final Set<String> DATES = Set.of("from", "to");
    private static final Set<String> STUDENT = Set.of("from", "to", "studentId");
    private static final Set<String> CLASS = Set.of("from", "to", "classId");
    private static final Set<String> DEPARTMENT = Set.of("from", "to", "departmentId");
    private static final Set<String> TERM = Set.of("from", "to", "examId");

    private final Map<String, ReportDefinition> byKey;

    public ReportRegistry(List<ReportDefinition> definitions) {
        Map<String, ReportDefinition> index = new LinkedHashMap<>();
        for (ReportDefinition definition : definitions) {
            if (index.put(definition.key(), definition) != null) {
                throw new IllegalStateException("Two reports are both called " + definition.key());
            }
        }
        this.byKey = Map.copyOf(index);
    }

    public List<ReportDefinition> all() {
        return byKey.values().stream()
                .sorted(java.util.Comparator.comparing(ReportDefinition::group)
                        .thenComparing(ReportDefinition::title))
                .toList();
    }

    /** The definitions a caller is allowed to run, grouped for a menu. */
    public Map<String, List<ReportDefinition>> visibleTo(java.util.function.Predicate<String> allowed) {
        Map<String, List<ReportDefinition>> groups = new LinkedHashMap<>();
        for (ReportDefinition definition : all()) {
            if (allowed.test(definition.permission())) {
                groups.computeIfAbsent(definition.group(), key -> new java.util.ArrayList<>())
                        .add(definition);
            }
        }
        return groups;
    }

    public ReportDefinition require(String key) {
        ReportDefinition definition = byKey.get(key);
        if (definition == null) {
            throw AppException.notFound("Report " + key);
        }
        return definition;
    }

    public java.util.Set<String> keys() {
        return byKey.keySet();
    }

    // ------------------------------------------------------------------ sets

    static Set<String> dates() {
        return DATES;
    }

    static Set<String> student() {
        return STUDENT;
    }

    static Set<String> classScoped() {
        return CLASS;
    }

    static Set<String> department() {
        return DEPARTMENT;
    }

    static Set<String> term() {
        return TERM;
    }
}
