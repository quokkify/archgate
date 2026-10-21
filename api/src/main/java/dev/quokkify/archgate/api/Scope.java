package dev.quokkify.archgate.api;

/**
 * The part of a project a rule or a layout query refers to.
 */
public enum Scope {
    /** Production code. */
    MAIN,
    /** Test code. */
    TEST
}
