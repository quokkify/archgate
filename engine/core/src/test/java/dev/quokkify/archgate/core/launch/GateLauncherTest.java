package dev.quokkify.archgate.core.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.launch.CleanLaunchRule;
import archfixtures.launch.LaunchContextProvider;
import archfixtures.launch.UnregisteredLaunchRule;
import archfixtures.launch.WarningLaunchRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.GateResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateLauncherTest {

    private static final String RULES = "META-INF/services/" + ArchitectureRule.class.getName();
    private static final String PROVIDERS = "META-INF/services/" + ContextProvider.class.getName();

    @TempDir
    Path tempDir;

    private final List<String> texts = new ArrayList<>();
    private final List<Severity> levels = new ArrayList<>();
    private final ReportSink sink = (level, text) -> {
        levels.add(level);
        texts.add(text);
    };

    @Test
    void passingRulesReturnResultAndWriteReport() throws IOException {
        Path report = tempDir.resolve("out/gate.txt");

        GateResult result = GateLauncher.run(request("WARNING", List.of(), report, CleanLaunchRule.class), sink);

        assertThat(result.outcomes()).hasSize(1);
        assertThat(report).exists();
        assertThat(Files.readString(report, StandardCharsets.UTF_8).lines())
                .anyMatch(line -> line.startsWith("Gate PASSED: 1 rule, no findings"));
        assertThat(levels).containsOnly(Severity.INFO);
        assertThat(texts).isNotEmpty();
    }

    @Test
    void warningAtDefaultThresholdThrowsGateFailureAfterReporting() {
        Path report = tempDir.resolve("gate.txt");

        assertThatThrownBy(() -> GateLauncher.run(request("WARNING", List.of(), report, WarningLaunchRule.class), sink))
                .isInstanceOfSatisfying(GateFailure.class, failure -> {
                    assertThat(failure)
                            .hasMessage("Architecture gate failed: 1 rule, 1 warning; failure policy: failOn=WARNING");
                    assertThat(failure.result().worst()).contains(Severity.WARNING);
                });
        assertThat(levels).isNotEmpty().containsOnly(Severity.WARNING);
        assertThat(report).exists();
    }

    @Test
    void failOnErrorLetsWarningPass() {
        GateResult result = GateLauncher.run(
                request("ERROR", List.of(), tempDir.resolve("gate.txt"), WarningLaunchRule.class), sink);

        assertThat(result.worst()).contains(Severity.WARNING);
        assertThat(levels).containsOnly(Severity.WARNING);
    }

    @Test
    void failOnNeverNeverThrows() {
        GateResult result = GateLauncher.run(
                request("NEVER", List.of(), tempDir.resolve("gate.txt"), WarningLaunchRule.class), sink);

        assertThat(result.worst()).contains(Severity.WARNING);
    }

    @Test
    void invalidRootPackageIsUnableToVerify() {
        LaunchRequest bad = new LaunchRequest(
                "not a package!",
                "WARNING",
                dirs(),
                List.of(),
                List.of(rulesDir(CleanLaunchRule.class)),
                Optional.empty());

        assertThatThrownBy(() -> GateLauncher.run(bad, sink)).isInstanceOf(UnableToVerifyError.class);
        assertThat(texts).isEmpty();
    }

    @Test
    void contextClassLoaderIsRestoredAfterFailure() {
        ClassLoader before = Thread.currentThread().getContextClassLoader();

        assertThatThrownBy(() -> GateLauncher.run(
                        request("WARNING", List.of(), tempDir.resolve("gate.txt"), WarningLaunchRule.class), sink))
                .isInstanceOf(GateFailure.class);

        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
    }

    @Test
    void unableToVerifyWritesNoReport() throws IOException {
        Path ownDir = tempDir.resolve("own");
        Path classFile = ownDir.resolve(UnregisteredLaunchRule.class.getName().replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        try (InputStream in = UnregisteredLaunchRule.class.getResourceAsStream("UnregisteredLaunchRule.class")) {
            Files.write(classFile, in.readAllBytes());
        }
        Path report = tempDir.resolve("gate.txt");

        assertThatThrownBy(() ->
                        GateLauncher.run(request("WARNING", List.of(ownDir), report, CleanLaunchRule.class), sink))
                .isInstanceOf(UnableToVerifyError.class);

        assertThat(report).doesNotExist();
        assertThat(texts).isEmpty();
    }

    @Test
    void unableToVerifyRemovesReportOfPreviousRun() throws IOException {
        Path report = tempDir.resolve("gate.txt");
        Files.writeString(report, "Gate PASSED: stale");
        LaunchRequest bad = new LaunchRequest(
                "not a package!",
                "WARNING",
                dirs(),
                List.of(),
                List.of(rulesDir(CleanLaunchRule.class)),
                Optional.of(report));

        assertThatThrownBy(() -> GateLauncher.run(bad, sink)).isInstanceOf(UnableToVerifyError.class);

        assertThat(report).doesNotExist();
    }

    private LaunchRequest request(String failOn, List<Path> ownDirs, Path report, Class<?> rule) {
        return new LaunchRequest("com.example", failOn, dirs(), ownDirs, List.of(rulesDir(rule)), Optional.of(report));
    }

    private ModuleDirs dirs() {
        return new ModuleDirs(List.of(), List.of(), List.of(), List.of());
    }

    private Path rulesDir(Class<?> rule) {
        try {
            Path dir = Files.createTempDirectory(tempDir, "rules");
            Files.createDirectories(dir.resolve("META-INF/services"));
            Files.writeString(dir.resolve(RULES), rule.getName() + "\n");
            Files.writeString(dir.resolve(PROVIDERS), LaunchContextProvider.class.getName() + "\n");
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
