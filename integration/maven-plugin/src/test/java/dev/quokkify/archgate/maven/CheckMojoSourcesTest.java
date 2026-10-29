package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testsupport.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

class CheckMojoSourcesTest {

    @Test
    void missingSourceRootsAreDropped() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path temp = tempDir.path();
            Path present = Files.createDirectory(temp.resolve("java"));
            Path absent = temp.resolve("generated");

            assertThat(CheckMojo.existing(List.of(present.toString(), absent.toString())))
                    .containsExactly(present);
        }
    }
}
