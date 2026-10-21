# 0001. Opt-in rule registration

Status: accepted

## Context

A consumer wants to enable exactly the rules it chooses: some built-in, some of its own, some from a shared rule library. Three ways to enable them were considered:

- ship a services file inside the rules artifacts, so every rule on the classpath runs;
- list rule classes in the build file (the pom or `build.gradle`);
- let each consumer list rules in its own `META-INF/services` file.

A services file inside the rules artifacts would run every rule for everybody who adds the dependency, and two libraries with services files would silently merge their lists. Class names in a build file break silently on a rename and duplicate what `ServiceLoader` already reads.

A rule that is written but never listed must not pass unnoticed either: a forgotten registration looks exactly like a rule that found nothing.

## Decision

- A rule runs only if the consumer lists it in `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`. No `rules-*` module ships a services file, and none may ever add one.
- `RuleRegistry` loads the listed rules with `ServiceLoader`, de-duplicates them by type and treats an empty registry as an error.
- `RegistrationCheck` fails the build when a public, concrete class with a public no-arg constructor that implements `ArchitectureRule` exists in the gate module's own class directories but is not listed. It uses plain reflection over the class files, so `core` depends on no analysis library. Nested `public static` classes are candidates too and are listed by their binary name.
- Built-in rules are configured by subclassing, never by build-file settings: parameters go through a `protected` constructor and the severity through `severity()`. The subclass is then registered in place of the original.
- Failing to verify fails closed. `UnableToVerifyError` fails the build whatever `failOn` says: an empty class view, an unbuilt selected module, a registration problem, a rule that throws something other than a finding.

## Consequences

- The consumer sees the complete list of what runs in one file under version control, and a rename is caught by the compiler and the registration check.
- Adding a dependency on a rules artifact changes nothing until the consumer lists rules. This surprises new users, so the documentation states it prominently.
- Forgotten registrations are caught with a message that names the lines to add.
- Parameterised variants of a rule need a small subclass. There is no per-rule configuration in the build file, which keeps the build-file surface to `rootPackage`, `failOn`, `modules` and `skip`.
