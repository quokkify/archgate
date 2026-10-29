package dev.quokkify.archgate.context.archunit.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.List;
import org.testng.annotations.Test;

class ArchRuleChecksTest {

    private static final ArchRule NO_PLAIN_FOO =
            ArchRuleDefinition.noClasses().should().haveSimpleName("PlainFoo").allowEmptyShould(true);

    @Test
    void violationsOfEveryClassSetAreMergedInOrder() {
        JavaClasses first = new ClassFileImporter().importPackages("archfixtures.generated");
        JavaClasses second = new ClassFileImporter().importPackages("archfixtures.console");
        ArchRule everyClass = ArchRuleDefinition.noClasses()
                .should()
                .haveSimpleNameStartingWith("")
                .allowEmptyShould(true);

        List<String> violations = ArchRuleChecks.violations(everyClass, first, second);

        int firstIndex = indexOfDetail(violations, "archfixtures.generated");
        int secondIndex = indexOfDetail(violations, "archfixtures.console");
        assertThat(firstIndex).isNotNegative();
        assertThat(secondIndex).isGreaterThan(firstIndex);
    }

    @Test
    void satisfiedRuleReportsNothing() {
        JavaClasses console = new ClassFileImporter().importPackages("archfixtures.console");

        assertThat(ArchRuleChecks.violations(NO_PLAIN_FOO, console)).isEmpty();
    }

    private static int indexOfDetail(List<String> violations, String fragment) {
        for (int i = 0; i < violations.size(); i++) {
            if (violations.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
