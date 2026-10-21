package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckMojoSourcesTest {

    @TempDir
    Path temp;

    @Test
    void missingSourceRootsAreDropped() throws Exception {
        Path present = Files.createDirectory(temp.resolve("java"));
        Path absent = temp.resolve("generated");

        assertThat(CheckMojo.existing(List.of(present.toString(), absent.toString())))
                .containsExactly(present);
    }
}
