package dev.quokkify.archgate.rules.archunit;

import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.ArchUnitContexts;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

final class FixtureContexts {

    private FixtureContexts() {}

    static ArchUnitContext of(String fixturePackage) {
        Path classes = testClassesDir();
        return of(fixturePackage, List.of(classes), List.of(classes));
    }

    static ArchUnitContext of(String fixturePackage, List<Path> mainDirs, List<Path> testDirs) {
        return ArchUnitContexts.of(new SimpleProjectLayout(
                fixturePackage, mainDirs, testDirs, List.of(), List.of(), FixtureContexts.class.getClassLoader()));
    }

    private static Path testClassesDir() {
        try {
            return Path.of(FixtureContexts.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the test class directory", e);
        }
    }
}
