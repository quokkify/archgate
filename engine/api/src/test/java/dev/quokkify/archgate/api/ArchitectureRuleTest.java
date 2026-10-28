package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureRuleTest {

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
    void defaultScopesAreMainAndTest() {
        assertThat(RULE.scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
    }

    @Test
    void checkViolationsThrowsWithContractAndCount() {
        assertThatThrownBy(() -> RULE.checkViolations("C", List.of("a", "b")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("C")
                .hasMessageContaining("Violations (2):");
    }

    @Test
    void checkViolationsWithEmptyListPasses() {
        assertThatCode(() -> RULE.checkViolations("C", List.of())).doesNotThrowAnyException();
    }

    @Test
    void analysisContextHasNoBuildTimesByDefault() {
        AnalysisContext context = new AnalysisContext() {};

        assertThat(context.buildTimes()).isEmpty();
    }

    @Test
    void contextProviderCreatesContextFromLayout() {
        ProjectLayout layout = new ProjectLayout() {
            @Override
            public String rootPackage() {
                return "p";
            }

            @Override
            public List<Path> classDirs(Scope scope) {
                return List.of();
            }

            @Override
            public List<Path> sourceDirs(Scope scope) {
                return List.of();
            }

            @Override
            public ClassLoader classLoader() {
                return getClass().getClassLoader();
            }
        };
        AnalysisContext context = new AnalysisContext() {
            @Override
            public Map<String, Duration> buildTimes() {
                return Map.of("m", Duration.ofMillis(1));
            }
        };
        ContextProvider<AnalysisContext> provider = new ContextProvider<>() {
            @Override
            public Class<AnalysisContext> type() {
                return AnalysisContext.class;
            }

            @Override
            public AnalysisContext create(ProjectLayout projectLayout) {
                return context;
            }
        };

        assertThat(provider.create(layout).buildTimes()).containsOnlyKeys("m");
        assertThat(layout.rootPackage()).isEqualTo("p");
        assertThat(layout.classDirs(Scope.MAIN)).isEmpty();
        assertThat(layout.sourceDirs(Scope.TEST)).isEmpty();
        assertThat(layout.classLoader()).isNotNull();
        assertThat(provider.type()).isEqualTo(AnalysisContext.class);
    }
}
