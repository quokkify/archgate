# 0010. Rules reach run services through `AnalysisContext`

Status: accepted

## Context

Rules need two things that belong to the whole gate run, not to one class view: the variables of the run (`Environment`) and values shared between rules (`shared`). A rule already receives an `AnalysisContext`, and `verify(C, ...)` is the method every rule overrides. The run services had to reach rules without breaking existing rules and without a second way to write one.

## Decision

- `AnalysisContext` has default methods `environment()` and `shared(name, build)`. The defaults throw an actionable `UnableToVerifyError` that names the context class, so a context without services fails visibly instead of misbehaving. `ProjectLayout.services()` has the same default.
- The directory-based contexts of each backend override them and delegate to `ProjectLayout.services()`, so the gate supplies one `RunServices` object per run and every rule of the run sees the same one.
- Rules call `context.environment()` and `context.shared(...)`. Existing rules and contexts compile and behave as before, as long as the rules do not call the new methods.

## Alternatives rejected

- **A second `verify(C, GateServices)` overload.** A rule would override one of two methods and leave the other empty, and the engine would have to decide which one to call. Two entry points for one job invite rules that implement the wrong one.
- **Static access through a run-scoped `ScopedValue`.** The services would be hidden global state: a rule's inputs would no longer be visible in its signature, and a unit test would have to bind the scope around every call. A context passed as an argument is easy to fake.

## Consequences

- A new backend context must override both methods; otherwise rules that read the environment or share values fail the build with `UnableToVerifyError`.
- Adding another run-wide service later means one more default method on `AnalysisContext`, which does not break existing rules.
