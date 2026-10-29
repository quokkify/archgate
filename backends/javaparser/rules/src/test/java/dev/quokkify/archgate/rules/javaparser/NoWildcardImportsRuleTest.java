package dev.quokkify.archgate.rules.javaparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.javaparser.JavaParserContext;
import dev.quokkify.archgate.context.javaparser.JavaParserContextProvider;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

class NoWildcardImportsRuleTest {

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(
                NoWildcardImportsRuleTest.class.getResource("/sources/" + name).toURI());
    }

    private static JavaParserContext context(String mainDir) throws URISyntaxException {
        SimpleProjectLayout layout = new SimpleProjectLayout(
                "com.example",
                List.of(),
                List.of(),
                List.of(resource(mainDir)),
                List.of(resource("test")),
                NoWildcardImportsRuleTest.class.getClassLoader());
        return new JavaParserContextProvider().create(layout);
    }

    @Test
    void wildcardImportIsReported() throws URISyntaxException {
        JavaParserContext context = context("main-wildcard");

        assertThatThrownBy(() -> new NoWildcardImportsRule().verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("Wildcard.java:3 import java.util.*");
    }

    @Test
    void staticWildcardIsAllowed() throws URISyntaxException {
        new NoWildcardImportsRule().verify(context("main-static"));
    }

    @Test
    void explicitImportsPass() throws URISyntaxException {
        new NoWildcardImportsRule().verify(context("main-explicit"));
    }

    @Test
    void contextTypeIsJavaParser() {
        assertThat(new NoWildcardImportsRule().contextType()).isEqualTo(JavaParserContext.class);
    }

    @Test
    void severityAndScopes() {
        NoWildcardImportsRule rule = new NoWildcardImportsRule();

        assertThat(rule.name()).isEqualTo("No wildcard imports");
        assertThat(rule.severity()).isEqualTo(Severity.WARNING);
        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
    }

    // regression: package/directory mismatch crashed the rule via Storage.getSourceRoot()
    @Test
    void packageDirectoryMismatchIsReportedRelativeToSourceDir() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path tmp = tempDir.path();
            Path mismatched = Files.createDirectories(tmp.resolve("main/foo"));
            Files.writeString(
                    mismatched.resolve("Bar.java"), "package com.example;\n\nimport java.util.*;\n\nclass Bar {}\n");
            SimpleProjectLayout layout = new SimpleProjectLayout(
                    "com.example",
                    List.of(),
                    List.of(),
                    List.of(tmp.resolve("main")),
                    List.of(tmp.resolve("main")),
                    NoWildcardImportsRuleTest.class.getClassLoader());
            JavaParserContext context = new JavaParserContextProvider().create(layout);

            assertThatThrownBy(() -> new NoWildcardImportsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("foo/Bar.java:3 import java.util.*");
        }
    }
}
