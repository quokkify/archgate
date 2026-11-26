package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;

class ArchitectureViolationTest {

    private static final String NL = System.lineSeparator();

    private static final ArchitectureRule<AnalysisContext> RULE = new ArchitectureRule<>() {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public Severity severity() {
            return Severity.ERROR;
        }

        @Override
        public Class<AnalysisContext> contextType() {
            return AnalysisContext.class;
        }

        @Override
        public void verify(AnalysisContext context) {}
    };

    @Test
    void messageListsContractAndViolations() {
        ArchitectureViolation violation = new ArchitectureViolation("C", List.of("a", "b"));

        assertThat(violation).hasMessage("C" + NL + "Violations (2):" + NL + "    - a" + NL + "    - b");
    }

    @Test
    void groupedMessageSkipsEmptyGroups() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "Contract", List.of(new ViolationGroup("a", List.of("x")), new ViolationGroup("b", List.of())));

        assertThat(violation).hasMessage("Contract" + NL + "Violations (1) \"a\":" + NL + "    - x");
    }

    @Test
    void checkViolationsIsNoOpWhenEmpty() {
        assertThatCode(() -> RULE.checkViolations("C", List.of())).doesNotThrowAnyException();
    }

    @Test
    void checkViolationsThrowsWhenNotEmpty() {
        assertThatThrownBy(() -> RULE.checkViolations("C", List.of("a")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("Violations (1):");
    }

    @Test
    void defaultScopesCoverMainAndTest() {
        assertThat(RULE.scopes()).containsExactlyInAnyOrder(Scope.MAIN, Scope.TEST);
    }

    @Test
    void severityIsOrderedFromLeastToMostSerious() {
        assertThat(Severity.values()).containsExactly(Severity.INFO, Severity.WARNING, Severity.ERROR);
    }

    @Test
    void groupedTextHasHeaderPerGroup() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "C",
                List.of(
                        new ViolationGroup("reason a", List.of("x1", "x2")),
                        new ViolationGroup("reason b", List.of("y1"))));

        assertThat(violation)
                .hasMessage("C" + NL + "Violations (2) \"reason a\":" + NL + "    - x1" + NL + "    - x2" + NL + NL
                        + "Violations (1) \"reason b\":" + NL + "    - y1");
    }

    @Test
    void repeatedGroupInstanceIsStillSeparated() {
        ViolationGroup g = new ViolationGroup("g", List.of("x"));
        ViolationGroup h = new ViolationGroup("h", List.of("y"));

        assertThat(ArchitectureViolation.grouped("C", List.of(g, h, g)))
                .hasMessage(String.join(
                        NL,
                        "C",
                        "Violations (1) \"g\":",
                        "    - x",
                        "",
                        "Violations (1) \"h\":",
                        "    - y",
                        "",
                        "Violations (1) \"g\":",
                        "    - x"));
    }

    @Test
    void emptyGroupsAreDropped() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "C",
                List.of(
                        new ViolationGroup("reason a", List.of("x1")),
                        new ViolationGroup("empty", List.of()),
                        new ViolationGroup("reason b", List.of("y1"))));

        assertThat(violation)
                .hasMessage("C" + NL + "Violations (1) \"reason a\":" + NL + "    - x1" + NL + NL
                        + "Violations (1) \"reason b\":" + NL + "    - y1");
    }

    @Test
    void checkViolationGroupsWithOnlyEmptyGroupsPasses() {
        assertThatCode(() -> RULE.checkViolationGroups("C", List.of(new ViolationGroup("empty", List.of()))))
                .doesNotThrowAnyException();
        assertThatCode(() -> RULE.checkViolationGroups("C", List.of())).doesNotThrowAnyException();
    }

    @Test
    void checkViolationGroupsThrowsWhenAnyGroupHasViolations() {
        assertThatThrownBy(() -> RULE.checkViolationGroups(
                        "C",
                        List.of(new ViolationGroup("empty", List.of()), new ViolationGroup("reason", List.of("a")))))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessage("C" + NL + "Violations (1) \"reason\":" + NL + "    - a");
    }

    @Test
    void groupCopiesItsViolationsAndRejectsNull() {
        List<String> lines = new ArrayList<>(List.of("a"));
        ViolationGroup group = new ViolationGroup("reason", lines);
        lines.add("b");

        assertThat(group.violations()).containsExactly("a");
        assertThatThrownBy(() -> new ViolationGroup(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ViolationGroup("reason", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void countIsTheNumberOfViolations() {
        assertThat(new ArchitectureViolation("C", List.of("a", "b")).count()).isEqualTo(2);
        assertThat(ArchitectureViolation.grouped(
                                "C",
                                List.of(
                                        new ViolationGroup("r", List.of("a")),
                                        ViolationGroup.table("t", List.of("ID"), List.of(List.of("1"), List.of("2")))))
                        .count())
                .isEqualTo(3);
    }

    @Test
    void tableGroupAlignsColumnsAndHasNoTrailingSpaces() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "C",
                List.of(ViolationGroup.table(
                        "deleted",
                        List.of("CASE", "LOCATION", "X", "EXPECTED"),
                        List.of(
                                List.of("C25216", "A.java:26", "longer", "a"),
                                List.of("C3823206", "LongerName.java:51", "y", "b")))));

        assertThat(violation)
                .hasMessage("C" + NL + "Violations (2) \"deleted\":"
                        + NL + "    CASE      LOCATION            X       EXPECTED"
                        + NL + "    --------  ------------------  ------  --------"
                        + NL + "    C25216    A.java:26           longer  a"
                        + NL + "    C3823206  LongerName.java:51  y       b");
    }

    @Test
    void tableGroupViolationsAreJoinedRows() {
        ViolationGroup group =
                ViolationGroup.table("t", List.of("A", "B"), List.of(List.of("1", "x"), List.of("22", "y")));

        assertThat(group.violations()).containsExactly("1  x", "22  y");
        assertThat(group.isTable()).isTrue();
        assertThat(new ViolationGroup("r", List.of("a")).isTable()).isFalse();
    }

    @Test
    void emptyTableGroupIsDropped() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "C",
                List.of(new ViolationGroup("a", List.of("x1")), ViolationGroup.table("t", List.of("A"), List.of())));

        assertThat(violation).hasMessage("C" + NL + "Violations (1) \"a\":" + NL + "    - x1");
    }

    @Test
    void checkViolationGroupsWithOnlyEmptyTableGroupPasses() {
        assertThatCode(() ->
                        RULE.checkViolationGroups("C", List.of(ViolationGroup.table("t", List.of("A"), List.of()))))
                .doesNotThrowAnyException();
    }

    @Test
    void mixedFlatAndTableGroupsKeepOrder() {
        ArchitectureViolation violation = ArchitectureViolation.grouped(
                "C",
                List.of(
                        ViolationGroup.table("t", List.of("A"), List.of(List.of("1"))),
                        new ViolationGroup("f", List.of("x"))));

        assertThat(violation)
                .hasMessage(String.join(
                        NL,
                        "C",
                        "Violations (1) \"t\":",
                        "    A",
                        "    -",
                        "    1",
                        "",
                        "Violations (1) \"f\":",
                        "    - x"));
    }

    @Test
    void tableGroupRejectsInvalidCells() {
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A", "B"), List.of(List.of("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A"), List.of(List.of("a\nb"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A\rB"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViolationGroup("t", List.of("other"), List.of("A"), List.of(List.of("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViolationGroup("t", List.of(), List.of(), List.of(List.of("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ViolationGroup.table(null, List.of("A"), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A"), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A"), List.of(Arrays.asList((String) null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'t'");
        assertThatThrownBy(() -> ViolationGroup.table("t", Arrays.asList("A", null), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'t'");
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A"), Arrays.asList(List.of("1"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'t'");
    }

    @Test
    void tableGroupRejectsBlankHeader() {
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of(""), List.of(List.of(""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'t'");
        assertThatThrownBy(() -> ViolationGroup.table("t", List.of("A", " "), List.of(List.of("1", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'t'");
    }

    @Test
    void tableGroupCopiesItsRows() {
        List<String> row = new ArrayList<>(List.of("1"));
        List<List<String>> rows = new ArrayList<>(List.of(row));
        ViolationGroup group = ViolationGroup.table("t", List.of("A"), rows);
        row.set(0, "changed");
        rows.add(List.of("2"));

        assertThat(group.rows()).containsExactly(List.of("1"));
        assertThat(group.violations()).containsExactly("1");
    }

    @Test
    void flatGroupSignaturesStayBinaryCompatible() throws NoSuchMethodException {
        assertThat(ViolationGroup.class.getConstructor(String.class, List.class))
                .isNotNull();
        assertThat(ViolationGroup.class.getMethod("title").getReturnType()).isEqualTo(String.class);
        assertThat(ViolationGroup.class.getMethod("violations").getReturnType()).isEqualTo(List.class);
        assertThat(ArchitectureViolation.class.getConstructor(String.class, Collection.class))
                .isNotNull();
        assertThat(ArchitectureViolation.class
                        .getMethod("grouped", String.class, List.class)
                        .getReturnType())
                .isEqualTo(ArchitectureViolation.class);
    }

    @Test
    void tableGroupWithNullTitleAndNoColumnsIsNullPointer() {
        assertThatThrownBy(() -> ViolationGroup.table(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("title");
    }
}
