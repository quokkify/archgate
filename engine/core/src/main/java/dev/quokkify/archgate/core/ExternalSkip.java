package dev.quokkify.archgate.core;

/**
 * Why a rule did not run because something outside the build was unavailable.
 *
 * @param message what the rule reported, printed as the first detail line
 * @param reason why the run treats that as a skip, printed as the second detail line
 */
public record ExternalSkip(String message, String reason) {}
