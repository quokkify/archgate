package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import org.testng.annotations.Test;

class AnalysisContextScopesTest {

    private static final Set<Scope> BOTH = EnumSet.of(Scope.MAIN, Scope.TEST);

    private static AnalysisContext emptyIn(Scope... empty) {
        Set<Scope> emptyScopes = Set.of(empty);
        return new AnalysisContext() {
            @Override
            public boolean isEmpty(Scope scope) {
                return emptyScopes.contains(scope);
            }
        };
    }

    private static ArchitectureRule<AnalysisContext> rule(Set<Scope> scopes) {
        return new ArchitectureRule<>() {
            @Override
            public String name() {
                return "r";
            }

            @Override
            public Severity severity() {
                return Severity.ERROR;
            }

            @Override
            public Set<Scope> scopes() {
                return scopes;
            }

            @Override
            public Class<AnalysisContext> contextType() {
                return AnalysisContext.class;
            }

            @Override
            public void verify(AnalysisContext context) {}
        };
    }

    @Test
    void viewsAreNotEmptyByDefault() {
        AnalysisContext context = new AnalysisContext() {};

        assertThat(context.isEmpty(Scope.MAIN)).isFalse();
        assertThat(context.scopesToCheck(BOTH)).containsExactly(Scope.MAIN, Scope.TEST);
    }

    @Test
    void bothPresentChecksBoth() {
        assertThat(emptyIn().scopesToCheck(BOTH)).containsExactly(Scope.MAIN, Scope.TEST);
        assertThat(rule(BOTH).skippedScopes(emptyIn())).isEmpty();
    }

    @Test
    void mainOnlyChecksMainAndSkipsTest() {
        assertThat(emptyIn(Scope.TEST).scopesToCheck(BOTH)).containsExactly(Scope.MAIN);
        assertThat(rule(BOTH).skippedScopes(emptyIn(Scope.TEST))).containsExactly(Scope.TEST);
    }

    @Test
    void testOnlyChecksTestAndSkipsMain() {
        assertThat(emptyIn(Scope.MAIN).scopesToCheck(BOTH)).containsExactly(Scope.TEST);
        assertThat(rule(BOTH).skippedScopes(emptyIn(Scope.MAIN))).containsExactly(Scope.MAIN);
    }

    @Test
    void everyScopeEmptyIsUnableToVerify() {
        assertThatThrownBy(() -> emptyIn(Scope.MAIN, Scope.TEST).scopesToCheck(BOTH))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("(main and test) is empty")
                .hasMessageContaining("Build the modules first");
    }

    @Test
    void singleEmptyScopeIsUnableToVerify() {
        assertThatThrownBy(() -> emptyIn(Scope.TEST).scopesToCheck(EnumSet.of(Scope.TEST)))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("(test) is empty");
    }

    @Test
    void skippedScopesStayWithinRuleScopes() {
        assertThat(rule(EnumSet.of(Scope.MAIN)).skippedScopes(emptyIn(Scope.TEST)))
                .isEmpty();
    }
}
