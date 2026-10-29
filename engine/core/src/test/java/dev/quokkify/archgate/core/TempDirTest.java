package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

class TempDirTest {

    @Test
    void createsAndDeletesDirectory() throws IOException {
        TempDir dir = TempDir.create();
        Path nested = dir.resolve("a/b/file.txt");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "x");

        assertThat(dir.path()).isDirectory().startsWith(Path.of(System.getProperty("java.io.tmpdir")));
        assertThat(nested).exists();

        dir.close();

        assertThat(nested).doesNotExist();
        assertThat(dir.path()).doesNotExist();
    }

    @Test
    void closeTwiceIsHarmless() {
        TempDir dir = TempDir.create();
        dir.close();

        assertThatCode(dir::close).doesNotThrowAnyException();
    }
}
