package dev.quokkify.archgate.rules.classgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.classgraph.ClassGraphContext;
import dev.quokkify.archgate.context.classgraph.ClassGraphContextProvider;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceFileConsistencyRuleTest {

    private static final String GREETER_FILE = "META-INF/services/archfixtures.classgraph.Greeter";
    private static final String SPI_FILE = "META-INF/services/archfixtures.outside.Spi";

    private final ServiceFileConsistencyRule rule = new ServiceFileConsistencyRule();

    private static Path testClasses() throws URISyntaxException {
        return Path.of(ServiceFileConsistencyRuleTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static Path caseDir(String name) throws URISyntaxException {
        return Path.of(ServiceFileConsistencyRuleTest.class
                .getResource("/classgraph-cases/" + name)
                .toURI());
    }

    private static ClassGraphContext context(List<Path> main, List<Path> test) {
        return new ClassGraphContextProvider()
                .create(new SimpleProjectLayout(
                        "archfixtures.classgraph",
                        main,
                        test,
                        List.of(),
                        List.of(),
                        ServiceFileConsistencyRuleTest.class.getClassLoader()));
    }

    private static ClassGraphContext mainCase(String name) throws URISyntaxException {
        return context(List.of(testClasses(), caseDir(name)), List.of());
    }

    @Test
    void unlistedImplementationIsReported() throws Exception {
        try (ClassGraphContext context = mainCase("unlisted")) {
            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining(GREETER_FILE + ": archfixtures.classgraph.UnlistedGreeter is not listed")
                    .hasMessageContaining("Violations (1)")
                    .hasMessageNotContaining("ListedGreeter is not listed")
                    .hasMessageNotContaining("Spi");
        }
    }

    @Test
    void listedImplementationPasses() throws Exception {
        try (ClassGraphContext context = mainCase("listed")) {
            assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        }
    }

    @Test
    void abstractAndNonPublicImplementationsAreIgnored() throws Exception {
        try (ClassGraphContext context = mainCase("listed")) {
            assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        }
        try (ClassGraphContext context = mainCase("unlisted")) {
            assertThatThrownBy(() -> rule.verify(context))
                    .hasMessageNotContaining("AbstractGreeter")
                    .hasMessageNotContaining("HiddenGreeter");
        }
    }

    @Test
    void interfaceOutsideRootPackageIsResolved() throws Exception {
        try (ClassGraphContext context = mainCase("outside-unlisted")) {
            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining(SPI_FILE + ": archfixtures.classgraph.OutsideSpiImpl is not listed");
        }
    }

    @Test
    void violationsAreSortedAndDeduplicatedAcrossScopes() throws Exception {
        Path dir = caseDir("all-unlisted");
        try (ClassGraphContext context = context(List.of(testClasses(), dir), List.of(testClasses(), dir))) {
            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("Violations (3)")
                    .satisfies(error -> assertThat(error.getMessage())
                            .containsSubsequence(
                                    GREETER_FILE + ": archfixtures.classgraph.ListedGreeter is not listed",
                                    GREETER_FILE + ": archfixtures.classgraph.UnlistedGreeter is not listed",
                                    SPI_FILE + ": archfixtures.classgraph.OutsideSpiImpl is not listed"));
        }
    }

    @Test
    void testScopeIsCheckedAgainstItsOwnClassDirectories() throws Exception {
        try (ClassGraphContext context = context(List.of(), List.of(testClasses(), caseDir("unlisted")))) {
            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("UnlistedGreeter is not listed");
        }
    }

    @Test
    void servicesFileInAnotherScopeDoesNotCoverImplementations() throws Exception {
        try (ClassGraphContext context = context(List.of(testClasses(), caseDir("listed")), List.of(testClasses()))) {
            assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        }
    }

    @Test
    void architectureRuleServiceIsLeftToTheRegistrationCheck() throws Exception {
        try (ClassGraphContext context = mainCase("architecture-rule")) {
            assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        }
    }

    @Test
    void noServicesFileIsUnableToVerify() throws Exception {
        try (ClassGraphContext context = context(List.of(testClasses()), List.of())) {
            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("No META-INF/services file was found in");
        }
    }

    @Test
    void contextTypeIsClassGraph() {
        assertThat(rule.contextType()).isEqualTo(ClassGraphContext.class);
    }

    @Test
    void severityAndScopes() {
        assertThat(rule.name()).isEqualTo("Service file consistency");
        assertThat(rule.severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
    }
}
