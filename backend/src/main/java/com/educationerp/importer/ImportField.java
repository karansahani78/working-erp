package com.educationerp.importer;

import java.util.List;

/**
 * One field an import can fill, and how a value in a cell becomes it.
 *
 * <p>The definition is data rather than code so that the same file can be offered for several
 * record types, the mapping screen can describe what it wants, and validation has one place to
 * go wrong rather than one per importer.
 */
public record ImportField(
        String name,
        String label,
        FieldKind kind,
        boolean required,
        int maxLength,
        /** Header names that should be matched to this field without being asked. */
        List<String> aliases,
        /** The values accepted, when the field is a fixed set. */
        List<String> allowed) {

    public ImportField {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        allowed = allowed == null ? List.of() : List.copyOf(allowed);
    }

    public static ImportField text(String name, String label, boolean required, int maxLength,
                                   String... aliases) {
        return new ImportField(name, label, FieldKind.TEXT, required, maxLength,
                List.of(aliases), List.of());
    }

    public static ImportField date(String name, String label, boolean required,
                                   String... aliases) {
        return new ImportField(name, label, FieldKind.DATE, required, 0, List.of(aliases),
                List.of());
    }

    public static ImportField number(String name, String label, boolean required,
                                     String... aliases) {
        return new ImportField(name, label, FieldKind.NUMBER, required, 0, List.of(aliases),
                List.of());
    }

    public static ImportField email(String name, String label, String... aliases) {
        return new ImportField(name, label, FieldKind.EMAIL, false, 180, List.of(aliases),
                List.of());
    }

    public static ImportField choice(String name, String label, boolean required,
                                     List<String> allowed, String... aliases) {
        return new ImportField(name, label, FieldKind.CHOICE, required, 40, List.of(aliases),
                allowed);
    }

    /** How a value is read, and so what an unusable one looks like. */
    public enum FieldKind {
        TEXT,
        DATE,
        NUMBER,
        EMAIL,
        CHOICE
    }
}