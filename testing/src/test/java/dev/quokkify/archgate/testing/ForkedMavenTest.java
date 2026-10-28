package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.maven.shared.verifier.Verifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForkedMavenTest {

    @TempDir
    Path temp;

    @Test
    // regression: forked Maven used ambient JAVA_HOME (Java 17 in CI)
    void forkedMavenUsesTheTestJvm() throws Exception {
        Verifier verifier = ForkedMaven.pinJavaHome(new Verifier(temp.toString()));

        assertThat(verifier.getEnvironmentVariables()).containsEntry("JAVA_HOME", System.getProperty("java.home"));
    }
}
