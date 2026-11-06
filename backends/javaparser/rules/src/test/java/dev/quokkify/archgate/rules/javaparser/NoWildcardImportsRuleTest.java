package dev.quokkify.archgate.rules.javaparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
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

    private static JavaParserContext context(List<Path> main, List<Path> test) {
        return new JavaParserContextProvider()
                .create(new SimpleProjectLayout(
                        "com.example",
                        List.of(),
                        List.of(),
                        main,
                        test,
                        NoWildcardImportsRuleTest.class.getClassLoader()));
    }

    // regression guard for #43: a module without test sources was not checked at all
    @Test
    void mainOnlyModuleIsCheckedInMain() throws IOException, URISyntaxException {
        try (TempDir tempDir = TempDir.create()) {
            JavaParserContext context = context(
                    List.of(resource("main-wildcard")), List.of(tempDir.path().resolve("absent")));
            NoWildcardImportsRule rule = new NoWildcardImportsRule();

            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("Wildcard.java:3 import java.util.*");
            assertThat(rule.skippedScopes(context)).containsExactly(Scope.TEST);
        }
    }

    @Test
    void testOnlyModuleIsCheckedInTest() throws IOException, URISyntaxException {
        try (TempDir tempDir = TempDir.create()) {
            JavaParserContext context =
                    context(List.of(tempDir.path().resolve("absent")), List.of(resource("main-wildcard")));
            NoWildcardImportsRule rule = new NoWildcardImportsRule();

            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("Wildcard.java:3 import java.util.*");
            assertThat(rule.skippedScopes(context)).containsExactly(Scope.MAIN);
        }
    }

    @Test
    void strictSubclassFailsOnEmptyTestSources() throws IOException, URISyntaxException {
        NoWildcardImportsRule strict = new NoWildcardImportsRule() {
            @Override
            public boolean requireAllScopes() {
                return true;
            }
        };
        try (TempDir tempDir = TempDir.create()) {
            JavaParserContext context = context(
                    List.of(resource("main-explicit")), List.of(tempDir.path().resolve("absent")));

            assertThatThrownBy(() -> strict.verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("test source directories");
        }
    }

    @Test
    void bothScopesEmptyIsUnableToVerify() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path absent = tempDir.path().resolve("absent");
            JavaParserContext context = context(List.of(absent), List.of(absent));

            assertThatThrownBy(() -> new NoWildcardImportsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("(main and test) is empty");
        }
    }

    @Test
    void bothScopesPresentSkipNothing() throws URISyntaxException {
        JavaParserContext context = context("main-explicit");
        NoWildcardImportsRule rule = new NoWildcardImportsRule();

        rule.verify(context);

        assertThat(rule.skippedScopes(context)).isEmpty();
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
