package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.maven.executor.ExecutorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForkedMavenTest {

    @TempDir
    Path temp;

    @Test
    // regression: forked Maven used ambient JAVA_HOME (Java 17 in CI)
    void forkedMavenUsesTheTestJvm() {
        ExecutorRequest request = ForkedMaven.pinJavaHome(ExecutorRequest.mavenBuilder())
                .cwd(temp)
                .build();

        assertThat(request.environmentVariables())
                .hasValueSatisfying(environment ->
                        assertThat(environment).containsEntry("JAVA_HOME", System.getProperty("java.home")));
    }
}
