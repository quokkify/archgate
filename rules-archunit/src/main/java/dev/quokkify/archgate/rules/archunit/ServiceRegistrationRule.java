package dev.quokkify.archgate.rules.archunit;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class ServiceRegistrationRule implements ArchitectureRule<ArchUnitContext> {

    private static final String SERVICES_DIR = "META-INF/services";
    private static final String COMMENT_MARK = "#";
    private static final String EXPECTED_CONTRACT = """
      Every class listed in a META-INF/services provider file must exist, implement the service interface named \
      by the file, be a public concrete class and have a public no-arg constructor.""";

    @Override
    public String name() {
        return "Service registration";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<Path> classDirs = new ArrayList<>(context.layout().classDirs(Scope.MAIN));
        classDirs.addAll(context.layout().classDirs(Scope.TEST));
        if (classDirs.isEmpty()) {
            throw new UnableToVerifyError("""
          No class directories are configured, so no provider file can be checked.""");
        }
        URL[] urls = classDirs.stream()
                .filter(Files::isDirectory)
                .map(ServiceRegistrationRule::toUrl)
                .toArray(URL[]::new);
        try (URLClassLoader loader =
                new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            verify(classDirs, loader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close the provider class loader", e);
        }
    }

    void verify(List<Path> classDirs, ClassLoader loader) {
        List<ProviderFile> providerFiles = classDirs.stream()
                .flatMap(ServiceRegistrationRule::providerFiles)
                .toList();
        if (providerFiles.isEmpty()) {
            throw new UnableToVerifyError("""
          No %s file was found in %s, so no provider registration was verified. Build the modules first and check \
          the class directories passed to the verification.""".formatted(SERVICES_DIR, classDirs));
        }
        List<String> violations = new ArrayList<>();
        for (ProviderFile file : providerFiles) {
            violations.addAll(violationsOf(file, loader));
        }
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    private static URL toUrl(Path dir) {
        try {
            return dir.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<ProviderFile> providerFiles(Path classDir) {
        Path servicesDir = classDir.resolve(SERVICES_DIR);
        if (!Files.isDirectory(servicesDir)) {
            return Stream.empty();
        }
        try (Stream<Path> files = Files.list(servicesDir)) {
            return files.filter(Files::isRegularFile).sorted().toList().stream()
                    .map(file -> new ProviderFile(classDir, file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + servicesDir, e);
        }
    }

    private static List<String> violationsOf(ProviderFile providerFile, ClassLoader loader) {
        Path file = providerFile.file();
        String location = providerFile.displayPath();
        String serviceName = file.toFile().getName();
        Class<?> service;
        try {
            service = Class.forName(serviceName, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return List.of("%s: service interface %s not found".formatted(location, serviceName));
        }
        List<String> violations = new ArrayList<>();
        for (String providerName : providerNames(file)) {
            String problem = problemOf(providerName, service, loader);
            if (Objects.nonNull(problem)) {
                violations.add("%s: %s %s".formatted(location, providerName, problem));
            }
        }
        return violations;
    }

    private static String problemOf(String providerName, Class<?> service, ClassLoader loader) {
        Class<?> provider;
        try {
            provider = Class.forName(providerName, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return "class not found";
        }
        if (!service.isAssignableFrom(provider)) {
            return "not assignable to " + service.getName();
        }
        if (!Modifier.isPublic(provider.getModifiers())) {
            return "not public";
        }
        if (Modifier.isAbstract(provider.getModifiers()) || provider.isInterface()) {
            return "is abstract or an interface";
        }
        try {
            provider.getConstructor();
        } catch (NoSuchMethodException e) {
            return "has no public no-arg constructor";
        }
        return null;
    }

    private static List<String> providerNames(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(line -> line.contains(COMMENT_MARK) ? line.substring(0, line.indexOf(COMMENT_MARK)) : line)
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UnableToVerifyError("Cannot read provider file " + file, e);
        }
    }

    private record ProviderFile(Path classDir, Path file) {

        String displayPath() {
            return classDir.relativize(file).toString().replace('\\', '/');
        }
    }
}
