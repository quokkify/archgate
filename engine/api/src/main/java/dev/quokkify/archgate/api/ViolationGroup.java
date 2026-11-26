package dev.quokkify.archgate.api;

import java.util.List;
import java.util.Objects;

/**
 * A named set of violations that share one reason, for {@link ArchitectureViolation#grouped}.
 *
 * <p>A group is either flat ({@link #ViolationGroup(String, List)}), printed as one bulleted line per violation, or a
 * table ({@link #table}), printed as aligned columns under a header row. For a table group {@link #violations()}
 * holds one line per row, its cells joined by two spaces.
 *
 * @param title the reason the violations share; must not be {@code null}
 * @param violations one entry per violation; copied, so later changes to the source list have no effect
 * @param columns the column headers of a table group; empty for a flat group
 * @param rows one row of cells per violation of a table group; empty for a flat group
 */
public record ViolationGroup(String title, List<String> violations, List<String> columns, List<List<String>> rows) {

    private static final String CELL_SEPARATOR = "  ";

    /**
     * Creates a group and copies its lists.
     *
     * @param title the reason the violations share; must not be {@code null}
     * @param violations one entry per violation; for a table group, the rows with cells joined by two spaces
     * @param columns the column headers; empty for a flat group
     * @param rows one row of cells per violation; empty for a flat group
     * @throws NullPointerException if {@code title}, a list or a flat violation is {@code null}
     * @throws IllegalArgumentException if the table is malformed: rows without columns, a blank or {@code null}
     *     header, a {@code null} row or cell, a row whose size differs from the columns, a line break in a header or
     *     cell, or violations that are not the joined rows
     */
    public ViolationGroup {
        Objects.requireNonNull(title, "title");
        violations = List.copyOf(violations);
        columns = copyColumns(title, columns);
        rows = copyRows(title, rows);
        if (columns.isEmpty()) {
            if (!rows.isEmpty()) {
                throw new IllegalArgumentException("Group '%s' has rows but no columns".formatted(title));
            }
        } else {
            checkTable(title, columns, rows);
            if (!violations.equals(joined(rows))) {
                throw new IllegalArgumentException(
                        "Group '%s': violations must be its rows with cells joined by two spaces".formatted(title));
            }
        }
    }

    /**
     * Creates a flat group: one line per violation.
     *
     * @param title the reason the violations share; must not be {@code null}
     * @param violations one entry per violation
     * @throws NullPointerException if {@code title} or {@code violations} is {@code null}, or a violation is
     *     {@code null}
     */
    public ViolationGroup(String title, List<String> violations) {
        this(title, violations, List.of(), List.of());
    }

    /**
     * Creates a table group: the rule names the columns and passes one row of cells per violation; ArchGate aligns
     * the columns and prints a header row.
     *
     * @param title the reason the violations share; must not be {@code null}
     * @param columns the column headers; at least one, none blank
     * @param rows one row per violation, with exactly one cell per column
     * @return the group
     * @throws NullPointerException if {@code title}, {@code columns} or {@code rows} is {@code null}
     * @throws IllegalArgumentException if there is no column, a header is blank or {@code null}, a row or cell is
     *     {@code null}, a row has the wrong number of cells, or a header or cell contains a line break
     */
    public static ViolationGroup table(String title, List<String> columns, List<List<String>> rows) {
        Objects.requireNonNull(title, "title");
        List<String> headers = copyColumns(title, columns);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Table group '%s' needs at least one column".formatted(title));
        }
        List<List<String>> copied = copyRows(title, rows);
        return new ViolationGroup(title, joined(copied), headers, copied);
    }

    /**
     * Tells whether this group is printed as a table.
     *
     * @return {@code true} for a group made by {@link #table}
     */
    public boolean isTable() {
        return !columns.isEmpty();
    }

    private static List<String> copyColumns(String title, List<String> columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.stream().anyMatch(header -> Objects.isNull(header) || header.isBlank())) {
            throw new IllegalArgumentException("Table group '%s': a column header is null or blank".formatted(title));
        }
        return List.copyOf(columns);
    }

    private static List<List<String>> copyRows(String title, List<List<String>> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.stream().anyMatch(row -> Objects.isNull(row) || row.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException("Table group '%s': a row or a cell is null".formatted(title));
        }
        return rows.stream().map(List::copyOf).toList();
    }

    private static List<String> joined(List<List<String>> rows) {
        return rows.stream().map(row -> String.join(CELL_SEPARATOR, row)).toList();
    }

    private static void checkTable(String title, List<String> columns, List<List<String>> rows) {
        columns.forEach(header -> checkCell(title, header));
        for (List<String> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("Table group '%s': a row has %d cells, expected %d"
                        .formatted(title, row.size(), columns.size()));
            }
            row.forEach(cell -> checkCell(title, cell));
        }
    }

    private static void checkCell(String title, String cell) {
        if (cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Table group '%s': a cell contains a line break".formatted(title));
        }
    }
}
