import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

class TestSummaryListener implements TestListener {

    private final String taskPath

    TestSummaryListener(String taskPath) {
        this.taskPath = taskPath
    }

    @Override
    void beforeSuite(TestDescriptor suite) {}

    @Override
    void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.parent == null) {
            println "${taskPath}: ${result.resultType}, ${result.testCount} tests, " +
                    "${result.successfulTestCount} passed, ${result.skippedTestCount} skipped, " +
                    "${result.failedTestCount} failed"
        }
    }

    @Override
    void beforeTest(TestDescriptor test) {}

    @Override
    void afterTest(TestDescriptor test, TestResult result) {}
}
