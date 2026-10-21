package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RegistrationCheck {

    private static final String CLASS_SUFFIX = ".class";
    private static final Set<String> SKIPPED_NAMES = Set.of("module-info", "package-info");

    private RegistrationCheck() {}

    public static void verify(List<Path> ownClassDirs, List<ArchitectureRule<?>> registered, ClassLoader loader) {
        Set<String> registeredNames =
                registered.stream().map(rule -> rule.getClass().getName()).collect(Collectors.toSet());
        List<String> missing = ownClassDirs.stream()
                .filter(Files::isDirectory)
                .flatMap(RegistrationCheck::classNames)
                .distinct()
                .filter(name -> isInstantiableRule(name, loader))
                .filter(name -> !registeredNames.contains(name))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new UnableToVerifyError(message(missing));
        }
    }

    private static Stream<String> classNames(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(CLASS_SUFFIX))
                    .map(path -> dir.relativize(path).toString())
                    .map(relative -> relative.substring(0, relative.length() - CLASS_SUFFIX.length())
                            .replace(dir.getFileSystem().getSeparator(), "."))
                    .filter(name -> !name.contains("$") && !SKIPPED_NAMES.contains(simpleName(name)))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static boolean isInstantiableRule(String name, ClassLoader loader) {
        Class<?> type;
        try {
            type = Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError unloadable) {
            return false;
        }
        return ArchitectureRule.class.isAssignableFrom(type)
                && !type.isInterface()
                && Modifier.isPublic(type.getModifiers())
                && !Modifier.isAbstract(type.getModifiers())
                && hasPublicNoArgConstructor(type);
    }

    private static boolean hasPublicNoArgConstructor(Class<?> type) {
        try {
            return Modifier.isPublic(type.getDeclaredConstructor().getModifiers());
        } catch (NoSuchMethodException | LinkageError none) {
            return false;
        }
    }

    private static String message(List<String> missing) {
        StringBuilder text = new StringBuilder("Rule classes in this project are not registered in ")
                .append(RuleRegistry.SERVICES_FILE)
                .append(". Add these lines:");
        missing.forEach(name -> text.append(System.lineSeparator()).append("  ").append(name));
        return text.toString();
    }
}
