package dev.quokkify.archgate.rules.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

class NoThreadSleepRuleTest {

    private static List<String> violations() {
        JavaClasses sleep = new ClassFileImporter().importPackages("archfixtures.sleep");
        return ArchRuleChecks.violations(NoThreadSleepRule.definition(), sleep);
    }

    @Test
    void threadSleepWithMillisIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("ThreadSleepLong.pause()", "Thread.sleep(long)"));
    }

    @Test
    void threadSleepWithMillisAndNanosIsReported() {
        assertThat(violations())
                .anySatisfy(
                        detail -> assertThat(detail).contains("ThreadSleepNanos.pause()", "Thread.sleep(long, int)"));
    }

    @Test
    void threadSleepWithDurationIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("ThreadSleepDuration.pause()", "Thread.sleep(java.time.Duration)"));
    }

    @Test
    void timeUnitSleepIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("TimeUnitSleep.pause()", "TimeUnit.sleep(long)"));
    }

    @Test
    void awaitilityPasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("AwaitilityWait"));
    }

    @Test
    void ruleInspectsMainAndTestClasses() {
        assertThat(new NoThreadSleepRule().scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
        assertThat(new NoThreadSleepRule().severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void verifyReportsFixtureViolations() {
        NoThreadSleepRule rule = new NoThreadSleepRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.sleep")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("ThreadSleepLong.pause()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoThreadSleepRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
