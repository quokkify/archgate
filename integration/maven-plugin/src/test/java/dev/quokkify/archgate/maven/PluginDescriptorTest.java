package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.core.FailOn;
import dev.quokkify.archgate.core.ReportStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

class PluginDescriptorTest {

    private static final String MINIMUM_MAVEN_VERSION = "3.9.0";
    private static final Path DESCRIPTOR = Path.of(System.getProperty("archgate.descriptor"));
    private static final Pattern REQUIRED_MAVEN =
            Pattern.compile("<requiredMavenVersion>([^<]+)</requiredMavenVersion>");
    private static final Pattern FAIL_ON_DEFAULT = Pattern.compile("<failOn [^>]*default-value=\"([^\"]+)\"");
    private static final Pattern REPORT_STYLE_DEFAULT = Pattern.compile("<reportStyle [^>]*default-value=\"([^\"]+)\"");

    // regression: a Renovate bump of maven.api.version raised the consumers' minimum Maven to 3.10.0
    @Test
    void requiresOnlyTheSupportedMinimumMavenVersion() throws IOException {
        Matcher matcher = REQUIRED_MAVEN.matcher(Files.readString(DESCRIPTOR));

        assertThat(matcher.find()).as("requiredMavenVersion in %s", DESCRIPTOR).isTrue();
        assertThat(matcher.group(1)).isEqualTo(MINIMUM_MAVEN_VERSION);
    }

    @Test
    void failOnDefaultIsTheCoreDefault() throws IOException {
        Matcher matcher = FAIL_ON_DEFAULT.matcher(Files.readString(DESCRIPTOR));

        assertThat(matcher.find()).as("failOn default-value in %s", DESCRIPTOR).isTrue();
        assertThat(matcher.group(1)).isEqualTo(FailOn.DEFAULT.name());
    }

    @Test
    void reportStyleDefaultsToClassic() throws IOException {
        Matcher matcher = REPORT_STYLE_DEFAULT.matcher(Files.readString(DESCRIPTOR));

        assertThat(matcher.find())
                .as("reportStyle default-value in %s", DESCRIPTOR)
                .isTrue();
        assertThat(ReportStyle.parse(matcher.group(1))).isEqualTo(ReportStyle.CLASSIC);
    }
}
