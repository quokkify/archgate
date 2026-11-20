package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.testng.annotations.Test;

public class ChangedFilesTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("archfixtures.caserules", "archfixtures.changedfiles");
    private static final String EXISTING = "module/src/test/java/archfixtures/caserules/ExistingCasesTest.java";

    @Test(description = "A listed file marks its top-level class as changed, others are not")
    public void matchesTopLevelBySuffix() {
        ChangedFiles changed = ChangedFiles.of(List.of(EXISTING));

        assertThat(changed.contains(type("archfixtures.caserules.ExistingCasesTest")))
                .isTrue();
        assertThat(changed.contains(type("archfixtures.caserules.MissingCasesTest")))
                .isFalse();
    }

    @Test(description = "A nested class is changed when its source file is listed")
    public void matchesNestedClassThroughSourceFile() {
        ChangedFiles changed = ChangedFiles.of(List.of("a/src/test/java/archfixtures/changedfiles/OuterTest.java"));

        assertThat(changed.contains(type("archfixtures.changedfiles.OuterTest$Inner")))
                .isTrue();
    }

    @Test(description = "Regression guard: a path only ending with the same text does not match")
    public void suffixMatchesOnSlashBoundary() {
        ChangedFiles changed = ChangedFiles.of(List.of("x/archfixtures/caserules/XExistingCasesTest.java"));

        assertThat(changed.contains(type("archfixtures.caserules.ExistingCasesTest")))
                .isFalse();
    }

    @Test(description = "A listed path equal to the class key matches without a leading directory")
    public void matchesExactKey() {
        ChangedFiles changed = ChangedFiles.of(List.of("archfixtures/caserules/ExistingCasesTest.java"));

        assertThat(changed.contains(type("archfixtures.caserules.ExistingCasesTest")))
                .isTrue();
    }

    @Test(description = "Blank lines, surrounding spaces and CRLF line endings are ignored")
    public void ignoresBlankLinesAndCrlf() throws IOException {
        Path list = write("\r\n  a/archfixtures/caserules/ExistingCasesTest.java \r\n\n");

        ChangedFiles changed = ChangedFiles.from(context(list.toString())).orElseThrow();

        assertThat(changed.contains(type("archfixtures.caserules.ExistingCasesTest")))
                .isTrue();
    }

    @Test(description = "Backslash separators are read as slashes")
    public void normalizesBackslashes() {
        ChangedFiles changed =
                ChangedFiles.of(List.of("module\\src\\test\\java\\archfixtures\\caserules\\ExistingCasesTest.java"));

        assertThat(changed.contains(type("archfixtures.caserules.ExistingCasesTest")))
                .isTrue();
    }

    @Test(description = "An unset or blank variable means a full check")
    public void unsetOrBlankVariableIsEmpty() {
        assertThat(ChangedFiles.from(new FixtureContext(FIXTURES, Map.of()))).isEmpty();
        assertThat(ChangedFiles.from(context(" "))).isEmpty();
    }

    @Test(description = "Regression guard: a list file that cannot be read fails closed")
    public void missingFileFailsClosed() {
        String path = "/nonexistent/archgate/changed-files.txt";

        assertThatThrownBy(() -> ChangedFiles.from(context(path)))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining(path)
                .hasMessageContaining("archgate.changedFiles");
    }

    @Test(description = "Regression guard: a value that is not a valid path fails closed with a clear message")
    public void invalidPathFailsClosed() {
        assertThatThrownBy(() -> ChangedFiles.from(context("bad\u0000path")))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("archgate.changedFiles");
    }

    @Test(description = "The class key uses the source file name, falling back to the top-level class name")
    public void classKeyFallsBackToTopLevelName() {
        assertThat(ChangedFiles.classKey("a.b", Optional.of("X.java"), "Y")).isEqualTo("a/b/X.java");
        assertThat(ChangedFiles.classKey("a.b", Optional.empty(), "Y")).isEqualTo("a/b/Y.java");
        assertThat(ChangedFiles.classKey("", Optional.empty(), "Y")).isEqualTo("Y.java");
    }

    private static FixtureContext context(String value) {
        return new FixtureContext(FIXTURES, Map.of(ChangedFiles.VARIABLE, value));
    }

    private static Path write(String content) throws IOException {
        Path file = Files.createTempFile("changed-files", ".txt");
        file.toFile().deleteOnExit();
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static JavaClass type(String name) {
        return FIXTURES.get(name);
    }
}
