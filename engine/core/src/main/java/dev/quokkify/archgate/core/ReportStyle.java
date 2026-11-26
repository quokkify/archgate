package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Locale;
import java.util.Objects;

/** How the gate prints its report: one row per rule ({@link #CLASSIC}) or a summary table ({@link #TABLE}). */
public enum ReportStyle {
    CLASSIC,
    TABLE;

    private static final String RESERVED_DETAILED = "detailed";
    private static final String VALID_VALUES = "Use classic or table.";

    /**
     * Parses a {@code reportStyle} value, ignoring case and surrounding blanks.
     *
     * @param value the configured value; {@code null} or blank means {@link #CLASSIC}
     * @return the style
     * @throws UnableToVerifyError if the value names no available style
     */
    public static ReportStyle parse(String value) {
        String trimmed = Objects.isNull(value) ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return CLASSIC;
        }
        if (RESERVED_DETAILED.equalsIgnoreCase(trimmed)) {
            throw new UnableToVerifyError(
                    "reportStyle '%s' is not available yet. %s".formatted(RESERVED_DETAILED, VALID_VALUES));
        }
        try {
            return valueOf(trimmed.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException unknown) {
            throw new UnableToVerifyError(
                    "Unknown reportStyle value '%s'. %s".formatted(trimmed, VALID_VALUES), unknown);
        }
    }
}
