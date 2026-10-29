# 0006. TestNG and q4j run the test suite

Status: accepted

## Context

Two gaps in the test suite led to issue #23:

- The end-to-end test ran every fixture scenario one after another under both Maven and Gradle. It dominated the build, and nothing ran in parallel.
- A failure showed a long build log inside an assertion message. There was no per-run log file to open, and no structured record of the steps of a scenario.

JUnit 5 with a custom extension (path b) and TestNG with q4j (path a) could both close the gaps.

## Decision

Every module, including `build-logic`, runs its tests on TestNG with q4j (`dev.quokkify:testng`) and AssertJ.

q4j already provides what path b would have to rebuild: lifecycle logging, `@SingleThread` and suite splitting, step logging, and Allure results. One runner across all modules also removes the second set of conventions.

q4j is a `testImplementation` dependency of every module. This relaxes the issue's constraint that only `:testing` may use it. The production configurations (`api`, `implementation`, `compileOnly`) stay free of q4j, SLF4J, Log4j and Allure.

JUnit stays in the catalog only as `junit-jupiter-api`, to compile the `archfixtures` sources of `rules-archunit`. `NoDisabledTestsRule`, `TestAssertionsRule` and `TestClassNamingRule` still recognise the JUnit annotations as string constants.

## Consequences

- Tests run in parallel by method. `TEST_THREAD_COUNT` defaults to 4 (`config/test-resources/testng.properties`); `DATA_PROVIDER_THREAD_COUNT` defaults to 4 (set in the environment of `:testing:test`). The end-to-end scenarios are a parallel data provider.
- TestNG shares one instance between methods, so a test class must hold no mutable instance state. Code that shares process or cache state, such as a static counter, POSIX permissions or the TestKit build and configuration caches, carries `@SingleThread` with a one-line reason.
- Each end-to-end build run writes `build/test-logs/<scenario>/<tool>-run<run>.log` in the `:testing` directory. A failure names the log file as its cause. Allure results go to `build/allure-results` of each module.
- `:gradle-plugin` and `:testing` exclude `log4j-slf4j2-impl` from their test runtime classpath, because the Gradle API and TestKit bring Gradle's own SLF4J provider and two providers would clash. `ScenarioLog` and `FixtureEndToEndTest` therefore log through the Log4j 2 API (`LogManager`, `ThreadContext`) instead of SLF4J.
- The archfixtures classes carry TestNG annotations, so the `rules-archunit` test task excludes them.
- A full `./gradlew clean build --no-build-cache` took 101.4 s on JUnit 5 and now takes 75.3 s, about 73 s and 73.6 s in three runs; `FixtureEndToEndTest` went from 51.6 s to 18.2 s.
