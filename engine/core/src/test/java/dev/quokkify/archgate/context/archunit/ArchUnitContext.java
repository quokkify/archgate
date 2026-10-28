package dev.quokkify.archgate.context.archunit;

import dev.quokkify.archgate.api.AnalysisContext;

/** Shares the binary name of the real ArchUnit context so tests assert the exact message a user sees. */
public interface ArchUnitContext extends AnalysisContext {}
