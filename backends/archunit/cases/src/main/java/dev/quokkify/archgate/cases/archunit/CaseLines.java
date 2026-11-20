package dev.quokkify.archgate.cases.archunit;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.SourceCodeLocation;
import dev.quokkify.archgate.api.ViolationGroup;
import java.util.Comparator;
import java.util.List;

final class CaseLines {

    private static final String PREFIX = "C";
    private static final int GAP = 2;

    private CaseLines() {}

    record Item(int id, List<JavaMethod> methods, String suffix) {}

    /** Builds one group ordered by id; ids sit in a column two characters wider than the longest of them. */
    static ViolationGroup group(String title, List<Item> items) {
        int width = items.stream()
                .mapToInt(item -> (PREFIX + item.id()).length())
                .max()
                .orElse(0);
        return new ViolationGroup(
                title,
                items.stream()
                        .sorted(Comparator.comparingInt(Item::id))
                        .map(item -> line(item.id(), item.methods(), width, item.suffix()))
                        .toList());
    }

    /**
     * Formats one finding: the id in a column of {@code idWidth + 2} characters, the {@code File.java:line} of the
     * first linking method, a count of any further methods and the suffix.
     */
    static String line(int id, List<JavaMethod> methods, int idWidth, String suffix) {
        SourceCodeLocation location = methods.get(0).getSourceCodeLocation();
        String more = methods.size() > 1 ? " (+%d more)".formatted(methods.size() - 1) : "";
        String paddedId = String.format("%-" + (idWidth + GAP) + "s", PREFIX + id);
        return "%s%s:%d%s%s".formatted(paddedId, location.getSourceFileName(), location.getLineNumber(), more, suffix);
    }
}
