package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
