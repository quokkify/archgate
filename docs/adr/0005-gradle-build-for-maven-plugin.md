# 0005. Gradle builds the Maven plugin

Status: accepted

## Context

The repository builds entirely with Gradle, including the Maven plugin. A Maven plugin needs a `META-INF/maven/plugin.xml` descriptor and a `plugin-help.xml`, and its POM must say `maven-plugin` packaging. Maven normally produces these with `maven-plugin-plugin`.

The Gradle plugin `org.gradlex.maven-plugin-development` generates the descriptor from the `@Mojo` and `@Parameter` annotations. Its Gradle 9 support was not stated, so the first piece of work was a spike with a hand-written `plugin.xml` as the fallback.

## Decision

The spike showed that `org.gradlex.maven-plugin-development` 1.0.3 works on Gradle 9.7.1 with the configuration cache enabled:

- the descriptor and `plugin-help.xml` are generated and packaged into the jar;
- the published POM has `maven-plugin` packaging;
- a real Maven 3.9 build resolves and runs the goal, and `@Parameter` defaults flow through.

One thing is missing: the extension has no setting for the minimum Maven version, and the generated descriptor carries no `<requiredMavenVersion>`. Maven would then accept the plugin on any version.

The build therefore uses the generator and patches its output. A `doLast` step on `generateMavenPluginDescriptor` inserts `<requiredMavenVersion>3.9.0</requiredMavenVersion>`, taken from the version catalog's `mavenApi` entry, when the generator did not write one. See `maven-plugin/build.gradle`. `PluginDescriptorTest` guards the result: it fails if the element is missing or if the minimum moves.

`maven-core` and `maven-plugin-api` stay `compileOnly` at 3.9.0 so that the plugin compiles against the oldest API it supports.

## Consequences

- No hand-written `plugin.xml` has to be maintained, so a new parameter needs only the annotation.
- The patch is a small build-logic dependency on the generator's output layout. A generator upgrade that writes the element itself leaves the patch inert, and one that changes the layout fails `PluginDescriptorTest` instead of publishing a plugin without a Maven floor.
- Integration tests run against a real Maven 3.9.x distribution resolved as a Gradle dependency, so the declared floor is exercised.
