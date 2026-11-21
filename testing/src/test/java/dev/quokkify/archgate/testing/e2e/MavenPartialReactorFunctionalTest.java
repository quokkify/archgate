package dev.quokkify.archgate.testing.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.MavenCache;
import dev.quokkify.archgate.testing.MavenRun;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Runs the gate of a multi-module fixture in a partial Maven reactor. The gate registers only a rule over MAIN and
 * TEST, holds one test class of its own, and the violation sits in a sibling module, so a gate that sees only its own test classes passes. The full
 * reactor is installed first with the gate skipped, so the modules left out of the partial run resolve from the local
 * repository as they would on a developer machine.
 *
 * <p>The local repository is not promoted to the shared cache: it holds the fixture's own installed modules.
 */
class MavenPartialReactorFunctionalTest {

    private static final String SCENARIO = "multi-module-violation";
    private static final String GATE = "architecture";
    private static final String CLEAN = "org.apache.maven.plugins:maven-clean-plugin:clean";
    private static final String UNCHECKED = "modules api, tests are part of the reactor but not of this build";
    private static final String GATE_PASSED = "Gate PASSED";
    private static final String RULE_SERVICES =
            "architecture/src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule";
    private static final String GATE_RULE =
            "architecture/src/test/java/com/example/architecture/NoLegacyClassesRule.java";
    private static final String MARKER = """
            package com.example.architecture;

            public class ArchitectureMarker {}
            """;
    private static final String SLEEPER = """
            package com.example.api;

            public class Sleeper {

              public void pause() throws InterruptedException {
                Thread.sleep(10);
              }
            }
            """;

    /** Regression guard: {@code -pl <gate>} checked only the gate module and passed despite a violating sibling. */
    @Test
    void projectListWithoutAlsoMakeFailsClosed() {
        try (TempDir temp = TempDir.create()) {
            Path project = installed(temp);

            MavenRun.Outcome outcome = run(temp, project, List.of("verify", "-pl", GATE));

            assertThat(outcome.succeeded()).as(outcome.log()).isFalse();
            assertThat(outcome.log()).contains(UNCHECKED, "add -am to -pl").doesNotContain(GATE_PASSED);
        }
    }

    /** Regression guard: {@code -rf :<gate>} checked only the gate module and passed despite a violating sibling. */
    @Test
    void resumeFromGateFailsClosed() {
        try (TempDir temp = TempDir.create()) {
            Path project = installed(temp);

            MavenRun.Outcome outcome = run(temp, project, List.of("verify", "-rf", ":" + GATE));

            assertThat(outcome.succeeded()).as(outcome.log()).isFalse();
            assertThat(outcome.log()).contains(UNCHECKED).doesNotContain(GATE_PASSED);
        }
    }

    @Test
    void projectListWithAlsoMakeChecksTheSiblings() {
        try (TempDir temp = TempDir.create()) {
            Path project = installed(temp);

            MavenRun.Outcome outcome = run(temp, project, List.of(CLEAN, "verify", "-pl", GATE, "-am"));

            assertThat(outcome.succeeded()).as(outcome.log()).isFalse();
            assertThat(outcome.log())
                    .contains("com.example.api.Sleeper", "Gate FAILED")
                    .doesNotContain(UNCHECKED);
        }
    }

    private static Path installed(TempDir temp) {
        Path project = Fixture.materialize(SCENARIO, BuildTool.MAVEN, temp.resolve("project"));
        delete(project.resolve(GATE_RULE));
        write(project.resolve(GATE_RULE).resolveSibling("ArchitectureMarker.java"), MARKER);
        write(project.resolve(RULE_SERVICES), "dev.quokkify.archgate.rules.archunit.NoThreadSleepRule\n");
        write(project.resolve("api/src/main/java/com/example/api/Sleeper.java"), SLEEPER);
        MavenRun.Outcome outcome = run(temp, project, List.of(CLEAN, "install", "-Darchgate.skip=true"));
        assertThat(outcome.succeeded())
                .as("full reactor install%n%s", outcome.log())
                .isTrue();
        return project;
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + file, e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static MavenRun.Outcome run(TempDir temp, Path project, List<String> goals) {
        return MavenRun.execute(project, temp.resolve("m2"), MavenCache.fromSystemProperty(), goals);
    }
}
