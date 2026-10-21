package dev.quokkify.archgate.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExistingDirectoriesTest {

    @TempDir
    Path temp;

    @Test
    void keepsOnlyExistingDirectoriesInOrder() throws IOException {
        Path second = Files.createDirectories(temp.resolve("b/src/main/java"));
        Path first = Files.createDirectories(temp.resolve("a/src/main/java"));
        Path missing = temp.resolve("a/src/test/java");
        Path file = Files.writeString(temp.resolve("plain.txt"), "x");

        List<Path> existing =
                ExistingDirectories.of(List.of(first.toFile(), missing.toFile(), file.toFile(), second.toFile()));

        assertThat(existing).containsExactly(first, second);
    }

    @Test
    void emptyInputGivesEmptyList() {
        assertThat(ExistingDirectories.of(List.<File>of())).isEmpty();
    }
}
