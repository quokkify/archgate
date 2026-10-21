# 0004. Gradle: explicit module selection

Status: accepted

## Context

Maven's reactor gives the plugin the compiled output of every module in the build, so the Maven plugin checks the current module plus the reactor modules it directly depends on (except `pom` packaging) by default.

Gradle has no equivalent that is both cheap and correct. Reading the class directories of sibling projects directly would reach into their configuration at execution time, break the configuration cache and give the task wrong inputs, so the build cache and up-to-date checks could not be trusted. Scanning every project would check modules the consumer never meant to include.

## Decision

- The Gradle plugin checks only the project that applies it, unless modules are named explicitly with `archgate { modules(':api', ':domain') }`.
- Every project that applies the plugin exposes a consumable configuration, `archgateElements`, with its main and test class directories, their processed-resources directories (the service-file rules read `META-INF/services` from them) and its source directories. `modules(':x')` adds a project dependency on `:x`'s `archgateElements`, so Gradle sees the other projects' outputs as declared task inputs.
- Every project named in `modules(...)` must apply the plugin itself. Otherwise the task fails with a message that contains `does not apply dev.quokkify.archgate`.
- A project that only exposes classes to a gate in another module sets `archgate { skip = true }`. On a project that is not skipped, an unset `rootPackage` fails the build.
- The command line replaces `rootPackage` (`-Parchgate.rootPackage`), but `failOn` and `skip` are conventions: an explicit value in the build script wins over `-Parchgate.failOn` and `-Parchgate.skip`.
- The task `archgateCheck` declares its inputs (`testRuntimeClasspath`, class and source directories of the selected modules, `rootPackage`, `failOn`) and its output (`build/reports/archgate/report.txt`), so it is cacheable and safe for the configuration cache. The console report is not replayed when the task is `UP-TO-DATE` or `FROM-CACHE`.
- Known limitation: the source directories of sibling projects are captured at configuration time.
- The supported consumer Gradle version is 9.7.1 and newer.

## Consequences

- The Gradle and Maven plugins differ on purpose in the default module selection. The documentation says so, and the parity tests cover the single-module and explicit `modules` cases.
- Consumers write one line per checked project, and get correct up-to-date behaviour and build cache hits in return.
- A forgotten `skip = true` or `rootPackage` fails loudly instead of silently checking nothing.
