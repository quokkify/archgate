# 0003. Backend-neutral API with pluggable analysis contexts

Status: accepted

## Context

The first implementation of ArchGate passed ArchUnit class views straight into every rule. That tied the contract rules are written against to one analysis library, and it made it impossible to write a rule over something ArchUnit does not model: import declarations exist only in source files, and the contents of `META-INF/services` files are resources, not classes.

The goal was to let a backend and its rules be adopted, copied or replaced without touching the others, and to keep the engine free of any analysis library so that the consumer's ArchUnit version never conflicts with the plugin's.

## Decision

- The API is `ArchitectureRule<C extends AnalysisContext>`. A rule declares its context type through `contextType()` and receives an instance in `verify(C)`. The contract has no analysis library on its classpath.
- A context is built from a `ProjectLayout`: the root package, class and source directories per scope, and a class loader. A `ContextProvider<C>` creates the context for a layout.
- Each backend lives in its own pair of modules: `context-<backend>` holds the context interface, its implementation and its provider; `rules-<backend>` holds the rules written against it. Today these are ArchUnit, JavaParser (sources) and ClassGraph (classes and resources).
- Each context module registers its provider in its own `META-INF/services/dev.quokkify.archgate.api.ContextProvider`. This is the only services file a published module may contain.
- `ContextResolver` in `core` maps a rule's context type to a provider found through `ServiceLoader`, creates each context lazily and at most once per run, and closes it afterwards if it is `AutoCloseable`. A missing provider raises `UnableToVerifyError` that names the `archgate-context-<backend>` artifact to add.
- An empty model for a scope that a rule requests is an unable-to-verify condition, for every backend.
- Dependency directions: `rules-X` depends on `api` and `context-X`; `context-X` on `api` and its library; `core` on `api` only; the build plugins on `core`. Nothing depends on a `rules-*` module.

## Consequences

- Adding a backend is adding two modules and no change to `api` or `core`.
- A rule that needs a second kind of model has to choose one context, or declare a context that combines them.
- The type parameter on `ArchitectureRule` changed the rule signature compared with the original; the API version therefore starts fresh under the new coordinates.
- Consumers add the context artifact for custom rules and get it transitively from a built-in rules artifact. A missing provider fails the build with an actionable message instead of a class-loading error.
