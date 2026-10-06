package com.educationerp.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * The numbers a head of school and a finance office open the system to see.
 *
 * <p>Each figure is a tile with a label, a value and a note saying what it counts. No tile is a
 * decorative chart: a bar of nothing in particular is not information, and the blueprint asks for
 * operational figures rather than decoration.
 *
 * <p>A section the caller may not see is left out entirely rather than shown as zero. Zero is a
 * claim, and a principal who cannot see the finance section should not be shown a zero balance.
 */
public final class DashboardDtos {

    /**
     * One figure.
     *
     * @param label what it is
     * @param value the figure
     * @param unit money, people, days, or nothing for a count
     * @param detail the date range, or what is excluded, so the number can be trusted
     */
    public record Tile(String label, BigDecimal value, String unit, String detail) {

        public static Tile count(String label, long value, String detail) {
            return new Tile(label, BigDecimal.valueOf(value), "count", detail);
        }

        public static Tile money(String label, BigDecimal value, String detail) {
            return new Tile(label, value == null ? BigDecimal.ZERO : value, "money", detail);
        }

        public static Tile days(String label, long value, String detail) {
            return new Tile(label, BigDecimal.valueOf(value), "days", detail);
        }

        /**
         * A share of something, as a percentage to one decimal place.
         *
         * <p>Dividing by nothing is reported as zero rather than as a failed query, because a
         * school with no attendance marked today should still see the rest of its dashboard.
         */
        public static Tile rate(String label, long part, long whole, String detail) {
            if (whole == 0) {
                return new Tile(label, BigDecimal.ZERO, "percent",
                        detail + " (nothing to divide by yet)");
            }
            return new Tile(label, BigDecimal.valueOf(part)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(whole), 1, java.math.RoundingMode.HALF_UP),
                    "percent", detail);
        }
    }

    /** A group of related figures, with the permission needed to see it. */
    public record Section(String name, String requires, List<Tile> tiles) {
    }

    /**
     * Something that needs a person to look at it.
     *
     * @param severity how urgent, so a screen can put the worst first
     * @param kind what sort of thing it is
     * @param message what needs doing
     * @param count how many, when it is a number of records
     */
    public record Alert(String severity, String kind, String message, long count) {
    }

    public record PrincipalDashboard(String asOf, List<Section> sections, List<Alert> alerts) {
    }

    public record AccountantDashboard(String asOf, List<Section> sections, List<Alert> alerts) {
    }

    private DashboardDtos() {
    }
}