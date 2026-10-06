package com.educationerp.common.error;

import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Reads an enum out of a request without letting a bad value become a server error.
 *
 * <p>A raw {@code Enum.valueOf(...)} on caller-supplied text throws
 * {@link IllegalArgumentException}, which nothing downstream turns into a 400: it falls through
 * to the catch-all handler, so the client gets a 500 and the server logs a stack trace for what
 * is simply a typo. Callers here were inconsistent about it — some normalised and checked, some
 * did not — so this is the one place that decides.
 *
 * <p>Comparison is trimmed and upper-cased, matching how every other enum in the codebase is
 * written (mixed-case names such as {@code AD} and {@code HALF_DAY} are round-tripped, not
 * guessed at).
 */
public final class Enums {

    private Enums() {
    }

    /**
     * Parses {@code value}, or returns {@code fallback} when it is null or blank.
     *
     * @throws AppException VALIDATION_ERROR when the text does not name a constant of {@code type}
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String value, String field, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return parse(type, value, field);
    }

    /**
     * Parses a value that is required to be present.
     *
     * @throws AppException VALIDATION_ERROR when it is missing or not a constant of {@code type}
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, field + " is required.",
                    Map.of(field, field + " is required."));
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message(type, field),
                    Map.of(field, message(type, field)));
        }
    }

    private static <E extends Enum<E>> String message(Class<E> type, String field) {
        String allowed = Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return field + " must be one of: " + allowed + ".";
    }
}
