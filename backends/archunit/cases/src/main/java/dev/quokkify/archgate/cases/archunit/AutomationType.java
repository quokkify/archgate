package dev.quokkify.archgate.cases.archunit;

import java.util.Objects;
import java.util.Optional;

/**
 * The automation type of an existing case: a name the system defines, no type at all, a raw value the
 * service could not translate into a name, or no type because the service cannot read automation types at all.
 *
 * <p>A system with a list of named types maps each option to {@link #named(String)}; a system with a plain
 * automated flag maps it to two fixed names such as {@code Automated} and {@code Manual}.
 */
public final class AutomationType {

    private static final AutomationType NOT_SET = new AutomationType(Kind.NOT_SET, "");

    private enum Kind {
        NAMED,
        NOT_SET,
        UNKNOWN,
        UNAVAILABLE
    }

    private final Kind kind;
    private final String value;

    private AutomationType(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Returns a type the system names.
     *
     * @param name the name as the system shows it
     * @return the type
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static AutomationType named(String name) {
        return new AutomationType(Kind.NAMED, nonBlank(name, "name"));
    }

    /**
     * Returns the type of a case that has none.
     *
     * @return the type
     */
    public static AutomationType notSet() {
        return NOT_SET;
    }

    /**
     * Returns a type whose raw value has no name in the system, for example an option id that the field
     * definition does not list.
     *
     * @param rawValue the value as stored on the case
     * @return the type
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if {@code rawValue} is blank
     */
    public static AutomationType unknown(String rawValue) {
        return new AutomationType(Kind.UNKNOWN, nonBlank(rawValue, "rawValue"));
    }

    /**
     * Returns the type of a case when the system's automation types cannot be read at all, for example because the
     * field that holds them is not configured. Existence checks ignore it; the automated check fails as unable to
     * verify with the reason.
     *
     * @param reason an actionable explanation for the user, naming what to fix
     * @return the type
     * @throws NullPointerException if {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static AutomationType unavailable(String reason) {
        return new AutomationType(Kind.UNAVAILABLE, nonBlank(reason, "reason"));
    }

    /**
     * Returns the name of a named type.
     *
     * @return the name, or empty if the type is not set, unknown or unavailable
     */
    public Optional<String> name() {
        return kind == Kind.NAMED ? Optional.of(value) : Optional.empty();
    }

    /**
     * Describes the type as a finding shows it: {@code 'Name'}, {@code not set} or {@code unknown (value)}.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return switch (kind) {
            case NAMED -> "'" + value + "'";
            case NOT_SET -> "not set";
            case UNKNOWN -> "unknown (" + value + ")";
            case UNAVAILABLE -> "unavailable (" + value + ")";
        };
    }

    Optional<String> unavailableReason() {
        return kind == Kind.UNAVAILABLE ? Optional.of(value) : Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AutomationType type && kind == type.kind && value.equals(type.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }

    private static String nonBlank(String text, String what) {
        Objects.requireNonNull(text, what);
        if (text.isBlank()) {
            throw new IllegalArgumentException("An automation type " + what + " must not be blank");
        }
        return text;
    }
}
