/**
 * Public contract between ArchGate and the code that plugs into it: architecture rules
 * ({@link dev.quokkify.archgate.api.ArchitectureRule}) and analysis backends
 * ({@link dev.quokkify.archgate.api.AnalysisContext} built by a
 * {@link dev.quokkify.archgate.api.ContextProvider}).
 *
 * <p>Rules are registered in {@code META-INF/services/dev.quokkify.archgate.api.ArchitectureRule} and
 * providers in {@code META-INF/services/dev.quokkify.archgate.api.ContextProvider}, both on the
 * consumer's test runtime classpath.
 */
package dev.quokkify.archgate.api;
