package dev.quokkify.archgate.context.javaparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.javaparser.ast.CompilationUnit;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserContextTest {

    private static final String ROOT = "com.example";

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(
                JavaParserContextTest.class.getResource("/sources/" + name).toURI());
    }

    private static JavaParserContext context(String root, List<Path> main, List<Path> test) {
        SimpleProjectLayout layout = new SimpleProjectLayout(
                root, List.of(), List.of(), main, test, JavaParserContextTest.class.getClassLoader());
        return new JavaParserContextProvider().create(layout);
    }

    private static List<String> typeNames(List<CompilationUnit> units) {
        return units.stream()
                .map(unit -> unit.getPrimaryTypeName().orElseThrow())
                .sorted()
                .toList();
    }

    @Test
    void parsesOnlyRootPackage() throws URISyntaxException {
        JavaParserContext context = context(ROOT, List.of(resource("main")), List.of());

        assertThat(typeNames(context.compilationUnits(Scope.MAIN))).containsExactly("Alpha", "Beta");
        assertThat(context.rootPackage()).isEqualTo(ROOT);
    }

    @Test
    void parsesEachScopeOnce() throws URISyntaxException {
        JavaParserContext context = context(ROOT, List.of(resource("main")), List.of(resource("test")));

        List<CompilationUnit> first = context.compilationUnits(Scope.MAIN);

        assertThat(context.compilationUnits(Scope.MAIN)).isSameAs(first);
        assertThat(context.buildTimes()).containsOnlyKeys("javaparser main");
        assertThat(typeNames(context.compilationUnits(Scope.TEST))).containsExactly("AlphaTest");
        assertThat(context.buildTimes()).containsOnlyKeys("javaparser main", "javaparser test");
    }

    @Test
    void missingTestSourcesIsUnableToVerify(@TempDir Path tmp) throws URISyntaxException {
        JavaParserContext context = context(ROOT, List.of(resource("main")), List.of(tmp.resolve("absent")));

        assertThatThrownBy(() -> context.compilationUnits(Scope.TEST))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("No Java source under com.example")
                .hasMessageContaining("test source directories");
    }

    @Test
    void parseErrorIsUnableToVerify(@TempDir Path tmp) throws IOException {
        Path pkg = Files.createDirectories(tmp.resolve("com/example"));
        Files.writeString(pkg.resolve("Broken.java"), "package com.example;\n\npublic class Broken {\n");
        JavaParserContext context = context(ROOT, List.of(tmp), List.of());

        assertThatThrownBy(() -> context.compilationUnits(Scope.MAIN))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("Cannot parse")
                .hasMessageContaining("Broken.java");
    }

    @Test
    void providerTypeIsJavaParserContext() {
        assertThat(new JavaParserContextProvider().type()).isEqualTo(JavaParserContext.class);
    }

    @Test
    void relativePathIsRelativeToOwningSourceDir() throws URISyntaxException {
        JavaParserContext context = context(ROOT, List.of(resource("main")), List.of());

        assertThat(context.compilationUnits(Scope.MAIN))
                .extracting(context::relativePath)
                .containsExactly("com/example/Alpha.java", "com/example/sub/Beta.java");
    }

    @Test
    void foreignUnitHasNoRelativePath() {
        JavaParserContext context = context(ROOT, List.of(), List.of());

        assertThatThrownBy(() -> context.relativePath(new CompilationUnit()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
