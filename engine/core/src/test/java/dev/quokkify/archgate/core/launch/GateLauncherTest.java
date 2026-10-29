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
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.testng.annotations.Test;

class GateLauncherTest {

    private static final String RULES = "META-INF/services/" + ArchitectureRule.class.getName();
    private static final String PROVIDERS = "META-INF/services/" + ContextProvider.class.getName();

    private static final class Env implements AutoCloseable {

        final TempDir dir = TempDir.create();
        final Path tempDir = dir.path();
        final List<String> texts = new ArrayList<>();
        final List<Severity> levels = new ArrayList<>();
        final ReportSink sink = (level, text) -> {
            levels.add(level);
            texts.add(text);
        };

        @Override
        public void close() {
            dir.close();
        }
    }

    @Test
    void passingRulesReturnResultAndWriteReport() throws IOException {
        try (Env env = new Env()) {
            Path report = env.tempDir.resolve("out/gate.txt");

            GateResult result = GateLauncher.run(
                    request(env.tempDir, "WARNING", List.of(), report, CleanLaunchRule.class), env.sink);

            assertThat(result.outcomes()).hasSize(1);
            assertThat(report).exists();
            assertThat(Files.readString(report, StandardCharsets.UTF_8).lines())
                    .anyMatch(line -> line.startsWith("Gate PASSED: 1 rule, no findings"));
            assertThat(env.levels).containsOnly(Severity.INFO);
            assertThat(env.texts).isNotEmpty();
        }
    }

    @Test
    void warningAtDefaultThresholdThrowsGateFailureAfterReporting() {
        try (Env env = new Env()) {
            Path report = env.tempDir.resolve("gate.txt");

            assertThatThrownBy(() -> GateLauncher.run(
                            request(env.tempDir, "WARNING", List.of(), report, WarningLaunchRule.class), env.sink))
                    .isInstanceOfSatisfying(GateFailure.class, failure -> {
                        assertThat(failure)
                                .hasMessage(
                                        "Architecture gate failed: 1 rule, 1 warning; failure policy: failOn=WARNING");
                        assertThat(failure.result().worst()).contains(Severity.WARNING);
                    });
            assertThat(env.levels).isNotEmpty().containsOnly(Severity.WARNING);
            assertThat(report).exists();
        }
    }

    @Test
    void failOnErrorLetsWarningPass() {
        try (Env env = new Env()) {
            GateResult result = GateLauncher.run(
                    request(env.tempDir, "ERROR", List.of(), env.tempDir.resolve("gate.txt"), WarningLaunchRule.class),
                    env.sink);

            assertThat(result.worst()).contains(Severity.WARNING);
            assertThat(env.levels).containsOnly(Severity.WARNING);
        }
    }

    @Test
    void failOnNeverNeverThrows() {
        try (Env env = new Env()) {
            GateResult result = GateLauncher.run(
                    request(env.tempDir, "NEVER", List.of(), env.tempDir.resolve("gate.txt"), WarningLaunchRule.class),
                    env.sink);

            assertThat(result.worst()).contains(Severity.WARNING);
        }
    }

    @Test
    void invalidRootPackageIsUnableToVerify() {
        try (Env env = new Env()) {
            LaunchRequest bad = new LaunchRequest(
                    "not a package!",
                    "WARNING",
                    dirs(),
                    List.of(),
                    List.of(rulesDir(env.tempDir, CleanLaunchRule.class)),
                    Optional.empty());

            assertThatThrownBy(() -> GateLauncher.run(bad, env.sink)).isInstanceOf(UnableToVerifyError.class);
            assertThat(env.texts).isEmpty();
        }
    }

    @Test
    void contextClassLoaderIsRestoredAfterFailure() {
        try (Env env = new Env()) {
            ClassLoader before = Thread.currentThread().getContextClassLoader();

            assertThatThrownBy(() -> GateLauncher.run(
                            request(
                                    env.tempDir,
                                    "WARNING",
                                    List.of(),
                                    env.tempDir.resolve("gate.txt"),
                                    WarningLaunchRule.class),
                            env.sink))
                    .isInstanceOf(GateFailure.class);

            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
        }
    }

    @Test
    void unableToVerifyWritesNoReport() throws IOException {
        try (Env env = new Env()) {
            Path ownDir = env.tempDir.resolve("own");
            Path classFile =
                    ownDir.resolve(UnregisteredLaunchRule.class.getName().replace('.', '/') + ".class");
            Files.createDirectories(classFile.getParent());
            try (InputStream in = UnregisteredLaunchRule.class.getResourceAsStream("UnregisteredLaunchRule.class")) {
                Files.write(classFile, in.readAllBytes());
            }
            Path report = env.tempDir.resolve("gate.txt");

            assertThatThrownBy(() -> GateLauncher.run(
                            request(env.tempDir, "WARNING", List.of(ownDir), report, CleanLaunchRule.class), env.sink))
                    .isInstanceOf(UnableToVerifyError.class);

            assertThat(report).doesNotExist();
            assertThat(env.texts).isEmpty();
        }
    }

    @Test
    void unableToVerifyRemovesReportOfPreviousRun() throws IOException {
        try (Env env = new Env()) {
            Path report = env.tempDir.resolve("gate.txt");
            Files.writeString(report, "Gate PASSED: stale");
            LaunchRequest bad = new LaunchRequest(
                    "not a package!",
                    "WARNING",
                    dirs(),
                    List.of(),
                    List.of(rulesDir(env.tempDir, CleanLaunchRule.class)),
                    Optional.of(report));

            assertThatThrownBy(() -> GateLauncher.run(bad, env.sink)).isInstanceOf(UnableToVerifyError.class);

            assertThat(report).doesNotExist();
        }
    }

    private static LaunchRequest request(Path tempDir, String failOn, List<Path> ownDirs, Path report, Class<?> rule) {
        return new LaunchRequest(
                "com.example", failOn, dirs(), ownDirs, List.of(rulesDir(tempDir, rule)), Optional.of(report));
    }

    private static ModuleDirs dirs() {
        return new ModuleDirs(List.of(), List.of(), List.of(), List.of());
    }

    private static Path rulesDir(Path tempDir, Class<?> rule) {
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
