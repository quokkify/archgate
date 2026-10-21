package dev.quokkify.archgate.gradle;

import static dev.quokkify.archgate.gradle.GradleFixtureFunctionalTest.materialize;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ArchgateElementsFunctionalTest {

    private static final String GATE_TASK = ":architecture:archgateCheck";
    private static final String PLUGIN_LINE = "    id 'dev.quokkify.archgate'\n";
    private static final String MODULES_LINE = "    modules(':api', ':tests')\n";
    private static final String GATE_BUILD = "architecture/build.gradle";
    private static final String SKIP_BLOCK = "\narchgate {\n    skip = true\n}\n";
    private static final String ROOT_PACKAGE_LINE = "    rootPackage = 'com.example'\n";
    private static final String MISSING_ROOT_PACKAGE = "archgate: rootPackage is not set for project ':architecture'."
            + " Set archgate { rootPackage = '...' }, or archgate { skip = true } for a module that only exposes"
            + " archgateElements to a gate.";
    private static final String OWN_MAIN_CLASS = "architecture/src/main/java/com/example/architecture/Layers.java";

    @TempDir
    Path temp;

    @ParameterizedTest(name = "{0}")
    @MethodSource("cacheModes")
    void moduleWithoutPluginFailsWithHint(List<String> extraArgs) throws IOException {
        Path project = materialize("multi-module", temp.resolve("project"));
        edit(project.resolve("api/build.gradle"), text -> remove(remove(text, PLUGIN_LINE), SKIP_BLOCK));

        BuildResult result = runner(project, GATE_TASK, extraArgs).buildAndFail();

        assertThat(result.getOutput())
                .contains("does not apply dev.quokkify.archgate")
                .contains("project ':api'");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cacheModes")
    void gateWithoutRootPackageFails(List<String> extraArgs) throws IOException {
        Path project = materialize("multi-module", temp.resolve("project"));
        edit(project.resolve(GATE_BUILD), text -> remove(text, ROOT_PACKAGE_LINE));

        BuildResult result = runner(project, GATE_TASK, extraArgs).buildAndFail();

        assertThat(result.getOutput()).contains(MISSING_ROOT_PACKAGE);
    }

    @Test
    void elementsOnlyModulesStaySkipped() {
        Path project = materialize("multi-module", temp.resolve("project"));

        BuildResult result = runner(
                        project, "check", List.of("-Parchgate.skip=false", "-Parchgate.rootPackage=com.example"))
                .build();

        assertThat(result.task(":api:archgateCheck").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(result.task(":tests:archgateCheck").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(result.task(GATE_TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void siblingClassesAreBuiltFirst() throws IOException {
        Path project = materialize("multi-module", temp.resolve("project"));
        edit(
                project.resolve(GATE_BUILD),
                text -> text.lines()
                        .filter(line -> !line.contains("implementation project("))
                        .collect(Collectors.joining("\n", "", "\n")));

        BuildResult result = runner(project, GATE_TASK).build();

        List<String> executed =
                result.getTasks().stream().map(BuildTask::getPath).toList();
        assertThat(executed).contains(":api:compileJava", ":tests:compileJava", GATE_TASK);
        assertThat(executed.indexOf(":api:compileJava")).isLessThan(executed.indexOf(GATE_TASK));
        assertThat(executed.indexOf(":tests:compileJava")).isLessThan(executed.indexOf(GATE_TASK));
        assertThat(result.getOutput()).contains("Gate PASSED: 2 rules, no findings");
    }

    @Test
    void withoutModulesOnlyOwnProjectIsChecked() throws IOException {
        Path project = materialize("multi-module-violation", temp.resolve("project"));
        edit(project.resolve(GATE_BUILD), text -> remove(text, MODULES_LINE));
        write(project.resolve(OWN_MAIN_CLASS), "package com.example.architecture;\n\npublic class Layers {}\n");

        BuildResult result = runner(project, GATE_TASK).build();

        assertThat(result.getOutput())
                .contains("Gate PASSED: 2 rules, no findings")
                .doesNotContain("com.example.api.LegacyAccount")
                .doesNotContain("com.example.tests.LegacyFlow");
    }

    static Stream<List<String>> cacheModes() {
        return Stream.of(List.of(), List.of("--configuration-cache"));
    }

    private static GradleRunner runner(Path project, String task) {
        return runner(project, task, List.of());
    }

    private static GradleRunner runner(Path project, String task, List<String> extraArgs) {
        List<String> arguments = new ArrayList<>(List.of(task, "--stacktrace"));
        arguments.addAll(extraArgs);
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private static String remove(String text, String line) {
        assertThat(text).contains(line);
        return text.replace(line, "");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void edit(Path file, UnaryOperator<String> change) throws IOException {
        Files.writeString(file, change.apply(Files.readString(file, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}
