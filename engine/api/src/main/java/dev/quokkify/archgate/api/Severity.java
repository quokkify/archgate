package dev.quokkify.archgate.api;

/**
 * How serious a rule's findings are. Together with the configured {@code failOn} it decides whether a
 * finding fails the build.
 */
public enum Severity {
    /** Informational finding. */
    INFO,
    /** Finding that deserves attention. */
    WARNING,
    /** Finding that breaks an architectural contract. */
    ERROR
}
