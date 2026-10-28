package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenCacheTest {

    @TempDir
    Path temp;

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    @Test
    void tailArgumentPointsAtTheSharedCache() {
        MavenCache cache = new MavenCache(temp.resolve("shared"));

        assertThat(cache.tailArgument()).isEqualTo("-Dmaven.repo.local.tail=" + temp.resolve("shared"));
    }

    @Test
    void promoteCopiesThirdPartyArtifactsOnly() throws IOException {
        Path head = temp.resolve("head");
        Path shared = temp.resolve("shared");
        write(head.resolve("org/example/lib/1.0/lib-1.0.jar"), "jar");
        write(head.resolve("org/example/lib/1.0/lib-1.0.pom"), "pom");
        write(head.resolve("org/example/gone/1.0/gone-1.0.jar.lastUpdated"), "miss");
        write(head.resolve("org/example/gone/resolver-status.properties"), "status");
        write(head.resolve("dev/quokkify/archgate/archgate-core/0.0.0-SNAPSHOT/archgate-core.jar"), "ours");

        new MavenCache(shared).promote(head);

        assertThat(shared.resolve("org/example/lib/1.0/lib-1.0.jar")).hasContent("jar");
        assertThat(shared.resolve("org/example/lib/1.0/lib-1.0.pom")).hasContent("pom");
        assertThat(shared.resolve("org/example/gone/1.0/gone-1.0.jar.lastUpdated"))
                .doesNotExist();
        assertThat(shared.resolve("org/example/gone/resolver-status.properties"))
                .doesNotExist();
        assertThat(shared.resolve("dev/quokkify")).doesNotExist();
    }

    @Test
    void promoteKeepsExistingFiles() throws IOException {
        Path head = temp.resolve("head");
        Path shared = temp.resolve("shared");
        write(head.resolve("org/example/lib/1.0/lib-1.0.jar"), "new");
        write(shared.resolve("org/example/lib/1.0/lib-1.0.jar"), "old");

        new MavenCache(shared).promote(head);

        assertThat(shared.resolve("org/example/lib/1.0/lib-1.0.jar")).hasContent("old");
    }

    @Test
    void promoteOfMissingHeadIsNoOp() {
        Path shared = temp.resolve("shared");

        new MavenCache(shared).promote(temp.resolve("absent"));

        assertThat(shared).doesNotExist();
    }
}
