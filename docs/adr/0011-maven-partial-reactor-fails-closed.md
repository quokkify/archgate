# 0011. Maven: a partial reactor fails closed

Status: accepted

## Context

The Maven plugin selects the modules to check from the reactor: by default the gate module plus the reactor modules it directly depends on, or the artifactIds listed in `modules` ([ADR 0004](0004-gradle-explicit-modules.md) describes the default). It took the reactor from `MavenSession.getProjects()`, which holds only the projects of the current run. `mvn -pl <gate> verify`, `mvn -rf :<gate> verify` or a resume after a failure leave the gate's dependencies out of that list, so the default selection shrank to the gate module alone without a word. The gate module usually holds only `src/test`, so its MAIN view was empty, and a rule over MAIN and TEST checked the gate's own test classes, skipped MAIN under [ADR 0008](0008-partial-scope-check.md) and passed, while a sibling module broke the rule. A listed module left out of the run failed with "is not part of the reactor", which was wrong.

The modules left out of a run may be built and installed, but the plugin has no supported way to read classes from an artifact: class and source directories come from the reactor (contract 12 in `AGENTS.md`). Reading jars from the local repository would be a new resolution mechanism with its own staleness problems, and sources would still be missing.

## Decision

- Module selection runs against the whole multi-module build, `MavenSession.getAllProjects()` (falling back to `getProjects()` when it is not set). A `modules` entry that is not in the whole build still fails with "is not part of the reactor".
- Every selected module must also be part of the current run, `MavenSession.getProjects()`, matched by `groupId:artifactId`. Otherwise the goal fails with a `MojoExecutionException`, the plugin's unable-to-verify outcome, whatever `failOn` says. The message names the missing modules and the gate module, and says how to run correctly: add `-am` to `-pl`, list the modules in `-pl`, or build the full reactor; or pass `-Darchgate.skip=true` to skip the gate in that run.
- There is no switch that narrows the selection to the modules of the run. `-Darchgate.skip=true` already skips the gate visibly, and a `modules` list in the POM narrows the selection on purpose for every run.

## Consequences

- `mvn -pl <gate> verify` and `mvn -rf :<gate> verify` fail when the gate checks other modules, even if those modules were checked in an earlier run. A user who relied on these runs passing adds `-am`, builds the full reactor, or skips the gate for that run.
- `-pl <gate> -am`, the full reactor, a single-module project and a `modules` list whose modules are all in the run behave as before.
- Gradle is not affected: `modules(...)` adds task dependencies, so Gradle builds the listed projects in the same run.
