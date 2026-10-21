package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

    private static final String MINIMUM_MAVEN_VERSION = "3.9.0";
    private static final Path DESCRIPTOR = Path.of(System.getProperty("archgate.descriptor"));
    private static final Pattern REQUIRED_MAVEN =
            Pattern.compile("<requiredMavenVersion>([^<]+)</requiredMavenVersion>");

    // regression: a Renovate bump of maven.api.version raised the consumers' minimum Maven to 3.10.0
    @Test
    void requiresOnlyTheSupportedMinimumMavenVersion() throws IOException {
        Matcher matcher = REQUIRED_MAVEN.matcher(Files.readString(DESCRIPTOR));

        assertThat(matcher.find()).as("requiredMavenVersion in %s", DESCRIPTOR).isTrue();
        assertThat(matcher.group(1)).isEqualTo(MINIMUM_MAVEN_VERSION);
    }
}
