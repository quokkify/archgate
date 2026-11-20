package dev.quokkify.archgate.rules.archunit.tms;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of the annotation a test method is required to have: its fully qualified name plus two
 * optional settings, each of which yields a fresh instance.
 *
 * <p>The {@linkplain #requiringNonBlankValue() value check} reads a {@code String value()}; if the attribute is absent
 * or of another type, it is treated as blank. Repeatable annotations need {@link #withContainer(String)}, because a
 * method that repeats the annotation holds only the container, and without it that method would look unannotated.
 */
public final class RequiredAnnotation {

    private final String name;
    private final String container;
    private final boolean nonBlankValue;

    private RequiredAnnotation(String name, String container, boolean nonBlankValue) {
        this.name = name;
        this.container = container;
        this.nonBlankValue = nonBlankValue;
    }

    /**
     * Starts a requirement for one annotation type, identified by name.
     *
     * @param annotationName the annotation's binary name, with the package; nested types are written {@code Outer$Inner}
     * @return a requirement that neither looks at a container nor inspects the value
     * @throws NullPointerException if {@code annotationName} is {@code null}
     * @throws IllegalArgumentException if the name is empty, has no package, is padded with whitespace, or begins or
     *     ends with {@code .} or {@code $}
     */
    public static RequiredAnnotation of(String annotationName) {
        return new RequiredAnnotation(qualified(annotationName), null, false);
    }

    /**
     * Names the container type of a repeatable annotation. The compiler wraps two or more repetitions on one method in
     * that container, so the requirement looks inside it as well.
     *
     * @param containerName the container annotation's binary name, with the package
     * @return a new requirement that also recognises the container
     * @throws NullPointerException if {@code containerName} is {@code null}
     * @throws IllegalArgumentException if the name is empty, has no package, is padded with whitespace, or begins or
     *     ends with {@code .} or {@code $}
     */
    public RequiredAnnotation withContainer(String containerName) {
        return new RequiredAnnotation(name, qualified(containerName), nonBlankValue);
    }

    /**
     * Additionally demands that the {@code String value()} attribute holds more than whitespace. An annotation without
     * such an attribute, or with one of another type, fails this check. Repeatable annotations still need
     * {@link #withContainer(String)}.
     *
     * @return a new requirement that inspects the value
     */
    public RequiredAnnotation requiringNonBlankValue() {
        return new RequiredAnnotation(name, container, true);
    }

    String name() {
        return name;
    }

    String simpleName() {
        return name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
    }

    Optional<String> container() {
        return Optional.ofNullable(container);
    }

    boolean nonBlankValue() {
        return nonBlankValue;
    }

    private static String qualified(String annotationName) {
        Objects.requireNonNull(annotationName, "annotation name");
        if (annotationName.isEmpty()
                || annotationName.indexOf('.') < 0
                || !annotationName.equals(annotationName.strip())
                || annotationName.startsWith(".")
                || annotationName.endsWith(".")
                || annotationName.startsWith("$")
                || annotationName.endsWith("$")) {
            throw new IllegalArgumentException(
                    "Annotation name must be fully qualified, but was '" + annotationName + "'");
        }
        return annotationName;
    }
}
