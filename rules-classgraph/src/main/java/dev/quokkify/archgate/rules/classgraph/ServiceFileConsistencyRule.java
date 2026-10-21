package dev.quokkify.archgate.rules.classgraph;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.classgraph.ClassGraphContext;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class ServiceFileConsistencyRule implements ArchitectureRule<ClassGraphContext> {

    private static final String SERVICES_PREFIX = "META-INF/services/";
    private static final String COMMENT_MARK = "#";
    private static final String REGISTRATION_CHECKED_SERVICE = ArchitectureRule.class.getName();
    private static final String EXPECTED_CONTRACT = """
      Every public concrete class under the root package that implements a service interface with a \
      META-INF/services file in the same class directories must be listed in that file.""";

    @Override
    public String name() {
        return "Service file consistency";
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
    public Class<ClassGraphContext> contextType() {
        return ClassGraphContext.class;
    }

    @Override
    public void verify(ClassGraphContext context) {
        Set<String> violations = new TreeSet<>();
        boolean serviceFileFound = false;
        for (Scope scope : scopes()) {
            if (!hasClassDirectory(context, scope)) {
                continue;
            }
            ScanResult scan = context.scan(scope);
            Map<String, Set<String>> listed = listedProviders(scan);
            serviceFileFound |=
                    listed.keySet().stream().anyMatch(service -> !REGISTRATION_CHECKED_SERVICE.equals(service));
            listed.forEach((service, providers) -> violations.addAll(unlisted(scan, service, providers)));
        }
        if (!serviceFileFound) {
            throw new UnableToVerifyError(
                    "No META-INF/services file was found in %s, so no service registration was verified. Build the modules first and check the class directories passed to the verification."
                            .formatted(allClassDirs(context)));
        }
        checkViolations(EXPECTED_CONTRACT, List.copyOf(violations));
    }

    private static boolean hasClassDirectory(ClassGraphContext context, Scope scope) {
        return context.layout().classDirs(scope).stream().anyMatch(Files::isDirectory);
    }

    private static List<Path> allClassDirs(ClassGraphContext context) {
        return Stream.of(Scope.values())
                .flatMap(scope -> context.layout().classDirs(scope).stream())
                .toList();
    }

    private static Map<String, Set<String>> listedProviders(ScanResult scan) {
        Map<String, Set<String>> listed = new TreeMap<>();
        for (Resource resource : scan.getAllResources()) {
            String path = resource.getPath();
            if (!path.startsWith(SERVICES_PREFIX) || path.indexOf('/', SERVICES_PREFIX.length()) >= 0) {
                continue;
            }
            listed.computeIfAbsent(path.substring(SERVICES_PREFIX.length()), key -> new HashSet<>())
                    .addAll(providerNames(resource));
        }
        return listed;
    }

    private static Set<String> providerNames(Resource resource) {
        try (resource) {
            Set<String> names = new HashSet<>();
            for (String line : resource.getContentAsString().split("\\R")) {
                String name =
                        (line.contains(COMMENT_MARK) ? line.substring(0, line.indexOf(COMMENT_MARK)) : line).strip();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource.getPath(), e);
        }
    }

    private static Set<String> unlisted(ScanResult scan, String service, Set<String> providers) {
        ClassInfo serviceInfo = scan.getClassInfo(service);
        if (Objects.isNull(serviceInfo) || REGISTRATION_CHECKED_SERVICE.equals(service)) {
            return Set.of();
        }
        ClassInfoList implementations =
                serviceInfo.isInterface() ? serviceInfo.getClassesImplementing() : serviceInfo.getSubclasses();
        Set<String> violations = new TreeSet<>();
        for (ClassInfo implementation : implementations) {
            if (isPublicConcreteClass(implementation) && !providers.contains(implementation.getName())) {
                violations.add("%s%s: %s is not listed".formatted(SERVICES_PREFIX, service, implementation.getName()));
            }
        }
        return violations;
    }

    private static boolean isPublicConcreteClass(ClassInfo info) {
        return !info.isExternalClass()
                && info.isStandardClass()
                && !info.isEnum()
                && info.isPublic()
                && !info.isAbstract();
    }
}
