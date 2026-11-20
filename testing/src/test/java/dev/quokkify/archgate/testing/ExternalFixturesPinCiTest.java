package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.testng.annotations.Test;

/**
 * Guards the fixtures that run external rules: a run that does not pin {@code archgate.ci} would take its
 * CI decision from the machine that runs the suite, and pass or fail depending on where it runs.
 */
class ExternalFixturesPinCiTest {

    private static final String ENVIRONMENT_CALL = ".environment()";
    private static final String CI_PIN_PREFIX = "archgate.ci=";

    @Test
    void everyRunOfAnExternalFixturePinsCi() {
        List<String> external = Fixture.scenarios().stream()
                .filter(scenario -> readsEnvironment(Fixture.root().resolve(scenario)))
                .toList();

        assertThat(external).contains("external-rule", "external-service-unavailable");
        for (String scenario : external) {
            for (Expectation run : Expectation.load(Fixture.root().resolve(scenario))) {
                assertThat(run.systemProperties())
                        .as("fixture %s run %d must set archgate.ci in systemProperties", scenario, run.run())
                        .anyMatch(property -> property.startsWith(CI_PIN_PREFIX));
            }
        }
    }

    private static boolean readsEnvironment(Path fixture) {
        try (Stream<Path> files = Files.walk(fixture)) {
            return files.filter(file -> file.toString().endsWith(".java")).anyMatch(file -> {
                try {
                    return Files.readString(file).contains(ENVIRONMENT_CALL);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot read " + file, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot walk " + fixture, e);
        }
    }
}
