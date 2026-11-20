package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

class GateRunServicesTest {

    private static GateRunServices services() {
        return new GateRunServices(new VariableEnvironment(name -> null, false));
    }

    @Test
    void environmentIsKept() {
        VariableEnvironment environment = new VariableEnvironment(name -> null, false);

        assertThat(new GateRunServices(environment).environment()).isSameAs(environment);
    }

    @Test
    void buildsOncePerName() throws Exception {
        GateRunServices services = services();
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return services.shared("load", () -> {
                        builds.incrementAndGet();
                        sleep(50);
                        return new Object();
                    });
                }));
            }
            start.countDown();
        }

        Object first = results.get(0).get();
        for (Future<Object> result : results) {
            assertThat(result.get()).isSameAs(first);
        }
        assertThat(builds).hasValue(1);
    }

    @Test
    void failureCachedAndRethrownSameInstance() {
        GateRunServices services = services();
        AtomicInteger builds = new AtomicInteger();
        IllegalStateException down = new IllegalStateException("down");

        Throwable first = catchThrowable(() -> services.shared("load", () -> {
            builds.incrementAndGet();
            throw down;
        }));
        Throwable second = catchThrowable(() -> services.shared("load", () -> {
            builds.incrementAndGet();
            return "never";
        }));

        assertThat(first).isSameAs(down);
        assertThat(second).isSameAs(down);
        assertThat(builds).hasValue(1);
    }

    @Test
    void checkedFailureIsUnableToVerify() {
        GateRunServices services = services();
        IOException io = new IOException("disk");

        assertThatThrownBy(() -> services.shared("load", () -> GateRunServicesTest.<RuntimeException>sneaky(io)))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("'load'")
                .hasCause(io);
    }

    @Test
    void reentranceFails() {
        GateRunServices services = services();

        assertThatThrownBy(() -> services.shared("load", () -> services.shared("load", () -> "inner")))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage(
                        "Shared value 'load' is requested again while it is being built, so it can never be built.");
    }

    @Test
    void differentNamesMayNest() {
        GateRunServices services = services();

        String value = services.shared("outer", () -> services.shared("inner", () -> "v") + "!");

        assertThat(value).isEqualTo("v!");
        assertThat(services.sharedBuildTimes()).containsOnlyKeys("inner", "outer");
    }

    @Test
    void buildTimesAreSortedByName() {
        GateRunServices services = services();
        services.shared("b", () -> 1);
        services.shared("a", () -> 2);
        services.shared("c", () -> 3);

        assertThat(services.sharedBuildTimes()).containsOnlyKeys("a", "b", "c");
        assertThat(services.sharedBuildTimes().keySet()).containsExactly("a", "b", "c");
    }

    @Test
    void buildTimeIsRecorded() {
        GateRunServices services = services();

        services.shared("slow", () -> {
            sleep(100);
            return 1;
        });

        assertThat(services.sharedBuildTimes().get("slow")).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }

    @Test
    void storeIsPerInstance() {
        AtomicInteger builds = new AtomicInteger();

        services().shared("load", builds::incrementAndGet);
        services().shared("load", builds::incrementAndGet);

        assertThat(builds).hasValue(2);
    }

    @Test
    void timeInSharedCountsOnlyTheOutermostCall() {
        GateRunServices services = services();
        GateRunServices.takeTimeInShared();

        long startedAt = System.nanoTime();
        services.shared(
                "outer",
                () -> services.shared("inner", () -> {
                    sleep(300);
                    return 1;
                }));
        long total = System.nanoTime() - startedAt;

        long inShared = GateRunServices.takeTimeInShared();
        assertThat(inShared).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(300));
        assertThat(inShared).isLessThanOrEqualTo(total);
        assertThat(GateRunServices.takeTimeInShared()).isZero();
    }

    @Test
    void timeInSharedIncludesFailedBuilds() {
        GateRunServices services = services();
        GateRunServices.takeTimeInShared();

        catchThrowable(() -> services.shared("load", () -> {
            sleep(100);
            throw new IllegalStateException("down");
        }));

        assertThat(GateRunServices.takeTimeInShared()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(100));
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> Object sneaky(Throwable failure) throws T {
        throw (T) failure;
    }
}
