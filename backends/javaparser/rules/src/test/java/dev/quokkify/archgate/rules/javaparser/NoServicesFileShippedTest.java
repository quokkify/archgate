package dev.quokkify.archgate.rules.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class NoServicesFileShippedTest {

    @Test
    void noServicesFileInJar() throws IOException {
        String jarPath = System.getProperty("archgate.rulesJar");
        assertThat(jarPath).isNotBlank();

        try (JarFile jar = new JarFile(jarPath)) {
            List<String> services = jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("META-INF/services/"))
                    .toList();

            assertThat(services).isEmpty();
        }
    }
}
