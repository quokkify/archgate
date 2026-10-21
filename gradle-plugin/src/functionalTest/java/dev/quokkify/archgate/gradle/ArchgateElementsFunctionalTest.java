package dev.quokkify.archgate.gradle;

import static dev.quokkify.archgate.gradle.GradleFixtureFunctionalTest.materialize;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchgateElementsFunctionalTest {

    private static final String GATE_TASK = ":architecture:archgateCheck";
    private static final String PLUGIN_LINE = "    id 'dev.quokkify.archgate'\n";
    private static final String MODULES_LINE = "    modules(':api', ':tests')\n";
    private static final String GATE_BUILD = "architecture/build.gradle";
    private static final String OWN_MAIN_CLASS = "architecture/src/main/java/com/example/architecture/Layers.java";

    @TempDir
    Path temp;

    @Test
    void moduleWithoutPluginFailsWithHint() throws IOException {
        Path project = materialize("multi-module", temp.resolve("project"));
        edit(project.resolve("api/build.gradle"), text -> remove(text, PLUGIN_LINE));

        BuildResult result = runner(project, GATE_TASK).buildAndFail();

        assertThat(result.getOutput())
                .contains("does not apply dev.quokkify.archgate")
                .contains("project ':api'");
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

    private static GradleRunner runner(Path project, String task) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(task, "--stacktrace");
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
