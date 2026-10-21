package dev.quokkify.archgate.rules.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceRegistrationRuleTest {

    private static final String FIXTURES = "archfixtures-services/";
    private static final String PROVIDER_FILE = "META-INF/services/archfixtures.services.Greeter";

    private final ServiceRegistrationRule rule = new ServiceRegistrationRule();

    private static Path caseDir(String name) {
        ClassLoader loader = ServiceRegistrationRuleTest.class.getClassLoader();
        URL url = loader.getResource(FIXTURES + name);
        if (Objects.isNull(url)) {
            throw new IllegalStateException("Missing fixture directory " + FIXTURES + name);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private void verifyCase(String name) {
        rule.verify(List.of(caseDir(name)), ServiceRegistrationRuleTest.class.getClassLoader());
    }

    private void assertViolation(String name, String... fragments) {
        assertThatThrownBy(() -> verifyCase(name))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining(PROVIDER_FILE)
                .hasMessageContainingAll(fragments);
    }

    @Test
    void validProviderPasses() {
        assertThatCode(() -> verifyCase("valid")).doesNotThrowAnyException();
    }

    @Test
    void commentsAndBlankLinesAreIgnoredIncludingTrailingComment() {
        assertThatCode(() -> verifyCase("commented")).doesNotThrowAnyException();
    }

    @Test
    void missingProviderClassIsReported() {
        assertViolation("missing", "archfixtures.services.NoSuchGreeter", "class not found");
    }

    @Test
    void providerNotImplementingSpiIsReported() {
        assertViolation(
                "not-spi",
                "archfixtures.services.UnrelatedProvider",
                "not assignable to archfixtures.services.Greeter");
    }

    @Test
    void providerWithoutNoArgConstructorIsReported() {
        assertViolation(
                "arg-constructor", "archfixtures.services.ArgConstructorGreeter", "no public no-arg constructor");
    }

    @Test
    void nonPublicProviderIsReported() {
        assertViolation("not-public", "archfixtures.services.HiddenGreeter", "not public");
    }

    @Test
    void abstractProviderIsReported() {
        assertViolation("abstract", "archfixtures.services.AbstractGreeter", "abstract or an interface");
    }

    @Test
    void interfaceProviderIsReported() {
        assertViolation("interface", "archfixtures.services.InterfaceGreeter", "abstract or an interface");
    }

    @Test
    void fileNamingNonExistentSpiIsReported() {
        assertThatThrownBy(() -> verifyCase("missing-spi"))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("META-INF/services/archfixtures.services.NoSuchSpi")
                .hasMessageContaining("service interface archfixtures.services.NoSuchSpi not found");
    }

    @Test
    void noProviderFilesThrowsError() {
        assertThatThrownBy(() -> verifyCase("no-providers"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("META-INF/services");
    }

    @Test
    void noClassDirectoriesIsUnableToVerify() {
        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures", List.of(), List.of())))
                .isInstanceOf(UnableToVerifyError.class);
    }

    // regression: provider in another module's test classes
    @Test
    void providerOnlyInAnotherTestDirIsResolved(@TempDir Path other) throws IOException {
        Path source = other.resolve("src/archfixtures/services/OffLoaderGreeter.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
        package archfixtures.services;
        public class OffLoaderGreeter implements Greeter {
          @Override
          public String greet() {
            return "off";
          }
        }
        """);
        Path classes = Files.createDirectories(other.resolve("classes"));
        String testClasspath = System.getProperty("java.class.path");
        int exit = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-cp", testClasspath, "-d", classes.toString(), source.toString());
        assertThat(exit).isZero();
        Path services = Files.createDirectories(classes.resolve("META-INF/services"));
        Files.writeString(
                services.resolve("archfixtures.services.Greeter"), "archfixtures.services.OffLoaderGreeter\n");

        ArchUnitContext context = FixtureContexts.of("archfixtures", List.of(), List.of(classes));

        assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
    }

    @Test
    void ruleIsAnErrorOverMainAndTest() {
        assertThat(rule.name()).isEqualTo("Service registration");
        assertThat(rule.severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new ServiceRegistrationRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
