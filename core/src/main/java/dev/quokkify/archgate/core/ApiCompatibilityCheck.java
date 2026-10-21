package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ApiCompatibilityCheck {

    private ApiCompatibilityCheck() {}

    static void verify(Optional<ApiVersion> runtime, Collection<? extends Class<?>> ruleTypes) {
        if (runtime.isEmpty()) {
            return;
        }
        List<Declaration> declarations = ruleTypes.stream()
                .flatMap(ApiCompatibilityCheck::withSuperclasses)
                .map(ApiVersionManifest::jar)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .flatMap(jar -> ApiVersionManifest.declared(jar).map(text -> Declaration.of(jar, text)).stream())
                .toList();
        List<Declaration> invalid = declarations.stream()
                .filter(declaration -> declaration.version().isEmpty())
                .toList();
        List<Declaration> newer = declarations.stream()
                .filter(declaration -> declaration
                        .version()
                        .filter(version -> version.compareTo(runtime.get()) > 0)
                        .isPresent())
                .toList();
        if (!invalid.isEmpty() || !newer.isEmpty()) {
            throw new UnableToVerifyError(message(runtime.get(), newer, invalid));
        }
    }

    private static Stream<Class<?>> withSuperclasses(Class<?> type) {
        return Stream.<Class<?>>iterate(type, Objects::nonNull, Class::getSuperclass);
    }

    private static String message(ApiVersion runtime, List<Declaration> newer, List<Declaration> invalid) {
        StringBuilder text = new StringBuilder();
        if (!newer.isEmpty()) {
            text.append("Rule libraries need a newer ArchGate API than the one running (")
                    .append(runtime)
                    .append("):");
            newer.forEach(declaration -> line(
                    text,
                    declaration.jarName() + " declares " + ApiVersionManifest.ATTRIBUTE + " " + declaration.text()));
            text.append(System.lineSeparator()).append(remedy(newer));
        }
        if (!invalid.isEmpty()) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append("Rule libraries declare an invalid ")
                    .append(ApiVersionManifest.ATTRIBUTE)
                    .append(':');
            invalid.forEach(declaration -> line(
                    text,
                    declaration.jarName() + " declares " + ApiVersionManifest.ATTRIBUTE + " '" + declaration.text()
                            + "', expected " + ApiVersion.EXPECTED_FORMAT));
            text.append(System.lineSeparator()).append("Fix the manifest entry in the build of these libraries.");
        }
        return text.toString();
    }

    private static String remedy(List<Declaration> newer) {
        ApiVersion required = newer.stream()
                .map(declaration -> declaration.version().orElseThrow())
                .max(ApiVersion::compareTo)
                .orElseThrow();
        return required.isDev()
                ? "Rebuild these libraries against a released archgate-api, or run the archgate plugin "
                        + ApiVersion.DEV + "."
                : "Raise the archgate plugin version to at least " + required + ".";
    }

    private static void line(StringBuilder text, String content) {
        text.append(System.lineSeparator()).append("  ").append(content);
    }

    private record Declaration(Path jar, String text, Optional<ApiVersion> version) {

        static Declaration of(Path jar, String text) {
            try {
                return new Declaration(jar, text, Optional.of(ApiVersion.parse(text)));
            } catch (IllegalArgumentException invalid) {
                return new Declaration(jar, text, Optional.empty());
            }
        }

        String jarName() {
            return Optional.ofNullable(jar.getFileName()).map(Path::toString).orElse(jar.toString());
        }
    }
}
