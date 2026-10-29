package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.testng.annotations.Test;

class MavenCacheTest {

    private static final int FILES = 50;
    private static final int THREADS = 8;

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    @Test
    void tailArgumentPointsAtTheSharedCache() {
        try (TempDir temp = TempDir.create()) {
            MavenCache cache = new MavenCache(temp.resolve("shared"));

            assertThat(cache.tailArgument()).isEqualTo("-Dmaven.repo.local.tail=" + temp.resolve("shared"));
        }
    }

    @Test
    void promoteCopiesThirdPartyArtifactsOnly() throws IOException {
        try (TempDir temp = TempDir.create()) {
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
    }

    @Test
    void promoteKeepsExistingFiles() throws IOException {
        try (TempDir temp = TempDir.create()) {
            Path head = temp.resolve("head");
            Path shared = temp.resolve("shared");
            write(head.resolve("org/example/lib/1.0/lib-1.0.jar"), "new");
            write(shared.resolve("org/example/lib/1.0/lib-1.0.jar"), "old");

            new MavenCache(shared).promote(head);

            assertThat(shared.resolve("org/example/lib/1.0/lib-1.0.jar")).hasContent("old");
        }
    }

    @Test
    void promoteOfMissingHeadIsNoOp() {
        try (TempDir temp = TempDir.create()) {
            Path shared = temp.resolve("shared");

            new MavenCache(shared).promote(temp.resolve("absent"));

            assertThat(shared).doesNotExist();
        }
    }

    @Test
    void concurrentPromotionsLeaveCompleteCache() throws Exception {
        try (TempDir temp = TempDir.create()) {
            Path head = temp.resolve("head");
            Path shared = temp.resolve("shared");
            List<Path> files = new ArrayList<>();
            for (int i = 0; i < FILES; i++) {
                files.add(head.relativize(
                        write(head.resolve("org/example/lib" + i % 5 + "/1.0/file-" + i + ".jar"), "content-" + i)));
            }
            MavenCache cache = new MavenCache(shared);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            try {
                List<Future<?>> promotions = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    promotions.add(executor.submit(() -> {
                        start.await();
                        cache.promote(head);
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> promotion : promotions) {
                    promotion.get();
                }
            } catch (ExecutionException e) {
                throw new AssertionError("concurrent promotion failed", e.getCause());
            } finally {
                executor.shutdownNow();
            }

            for (Path file : files) {
                assertThat(shared.resolve(file.toString())).hasSameBinaryContentAs(head.resolve(file.toString()));
            }
            try (Stream<Path> remaining = Files.walk(shared)) {
                assertThat(remaining.filter(path -> path.toString().endsWith(".part")))
                        .isEmpty();
            }
        }
    }
}
