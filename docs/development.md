# Development

## Requirements

- JDK 21. The build uses a Java 21 toolchain and compiles with `--release 21`.
- Nothing else: the Gradle wrapper (`./gradlew`) downloads Gradle, and the integration tests download a Maven 3.9.x distribution as a Gradle dependency.

## Build locally

```
./gradlew build
```

`build` runs `check`, which covers:

- unit tests of every module;
- `:testing:test`, the end-to-end test: every shared fixture runs once through a real Maven build (`maven-executor`) and once through a real Gradle build (TestKit), each checked against its `expected.properties`; for every fixture except `skip` the two reports must then be identical, and for a run marked `unableToVerify=true` neither tool may write a report and both must print the same message line. The same project holds the Gradle plugin's caching and `archgateElements` tests;
- Spotless (palantir-java-format) and Checkstyle (`config/checkstyle/checkstyle.xml`);
- JaCoCo coverage verification (80 % of lines) for `:api`, `:core`, `:context-*` and `:rules-*`;
- `DocsLinkTest`: relative links in the Markdown files resolve, the README rule table names existing rule classes, and the table lists every implemented rule;
- `denylistHookTest`: the pre-push hook blocks what it should.

To fix formatting: `./gradlew spotlessApply`.

### Useful tasks

| Task | What it does |
|---|---|
| `./gradlew build` | everything above |
| `./gradlew :testing:test` | the end-to-end test: every fixture under Maven and Gradle, results compared |
| `./gradlew :testing:test -PfixtureTools=gradle` | the fixtures under Gradle only (`maven` for Maven only); nothing is compared; the Gradle plugin's caching and `archgateElements` tests always run through Gradle TestKit |
| `./gradlew publishToItRepo` | publishes every module to `build/it-repo`, the local repository the integration tests resolve from |
| `./gradlew :testing:test --tests '*DocsLinkTest'` | only the documentation checks |

`:testing:test` depends on `publishToItRepo`, so it always tests freshly built artifacts. `publishToItRepo` first empties `build/it-repo`, and the tests take the published jars and POMs as inputs, so they are up to date while the code and the fixtures are unchanged. Each Maven run gets a fresh local repository for the ArchGate snapshots and reads third-party plugins and libraries from `build/maven-it-cache` (`-Dmaven.repo.local.tail`), which the runs fill as they go, so each artifact is downloaded once until `clean`.

### Shared fixtures

`testing/fixtures/<scenario>/` holds the sources and the services file of one scenario, plus `fixture.properties` and `expected.properties`. The test support in `:testing` generates a `build.gradle` or a `pom.xml` around it, so the same scenario runs under both tools. To add a scenario, add a directory there; the end-to-end test picks it up. Mark a run that must end with an "unable to verify" error with `run.N.unableToVerify=true`, so the end-to-end test compares its message instead of a report.

## Version

`version.txt` holds the last released version (`0.0.0` before the first release). A normal build appends `-SNAPSHOT`. A release build passes `-Prelease=true` and uses the version as is. Jars stamp `Archgate-Api-Version` in their manifest: the release version for release builds, `dev-SNAPSHOT` for every `-SNAPSHOT` build.

## Pre-push hook

The repository is public, so a local denylist of names that must never appear in it is checked before every push. The denylist file itself is local and is never committed.

1. Put one case-insensitive extended regular expression per line in `info/archgate-denylist` inside the common git directory:

   ```
   "$(git rev-parse --git-common-dir)/info/archgate-denylist"
   ```

2. Install the hook:

   ```
   ln -s ../../scripts/pre-push-denylist.sh "$(git rev-parse --git-common-dir)/hooks/pre-push"
   ```

   The common git directory is shared by linked worktrees, so one denylist and one hook serve all of them.

On every push the hook greps the pushed tree and the pushed commits (`git log -p`) with the denylist. A match, a missing denylist file or a tool error blocks the push. To check by hand:

```
git grep -niEf "$(git rev-parse --git-common-dir)/info/archgate-denylist"
```

It must print nothing.

## CI

Pull requests and pushes to `main` run the Gradle build with JDK 21. The workflows are generated from a shared template; see [Releasing](releasing.md) for the release and publishing workflows.
