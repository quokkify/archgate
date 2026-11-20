package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import archfixtures.caserules.MissingCasesTest;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ViolationGroup;
import java.util.List;
import org.testng.annotations.Test;

public class CaseLinesTest {

    private static final LinkedCases LINKED =
            LinkedCases.from(new ClassFileImporter().importClasses(MissingCasesTest.class));
    private static final List<JavaMethod> NINE = LINKED.byId().get(9);
    private static final List<JavaMethod> TEN = LINKED.byId().get(10);

    @Test(description = "Ids are aligned to the longest id of the group plus two spaces")
    public void alignsIdsWithinGroup() {
        int width = "C40917".length();

        assertThat(CaseLines.line(9, NINE, width, "")).matches("C9 {6}MissingCasesTest\\.java:\\d+");
        assertThat(CaseLines.line(40917, NINE, width, "")).matches("C40917 {2}MissingCasesTest\\.java:\\d+");
    }

    @Test(description = "Further methods of one id are counted, the location is the first method's by name")
    public void moreMethodsAreCounted() {
        int tenA = TEN.get(0).getSourceCodeLocation().getLineNumber();

        assertThat(TEN).extracting(JavaMethod::getName).containsExactly("tenA", "tenB");
        assertThat(CaseLines.line(10, TEN, 3, " (x)"))
                .isEqualTo("C10  MissingCasesTest.java:%d (+1 more) (x)".formatted(tenA));
    }

    @Test(description = "A group is sorted by id and aligned to its own longest id")
    public void groupSortsAndAligns() {
        ViolationGroup group = CaseLines.group(
                "reason", List.of(new CaseLines.Item(10, TEN, ""), new CaseLines.Item(9, NINE, " (y)")));

        assertThat(group.title()).isEqualTo("reason");
        assertThat(group.violations())
                .satisfiesExactly(
                        line -> assertThat(line).matches("C9   MissingCasesTest\\.java:\\d+ \\(y\\)"),
                        line -> assertThat(line).matches("C10  MissingCasesTest\\.java:\\d+ \\(\\+1 more\\)"));
    }
}
