package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;

/** Context over an already imported set of fixture classes, used as the test view. */
final class FixtureContext implements ArchUnitContext {

    private final String rootPackage;
    private final JavaClasses testClasses;

    FixtureContext(String rootPackage, JavaClasses testClasses) {
        this.rootPackage = rootPackage;
        this.testClasses = testClasses;
    }

    @Override
    public String rootPackage() {
        return rootPackage;
    }

    @Override
    public JavaClasses main() {
        return new ClassFileImporter().importClasses(Object.class);
    }

    @Override
    public JavaClasses test() {
        return testClasses;
    }

    @Override
    public JavaClasses all() {
        return testClasses;
    }

    @Override
    public ProjectLayout layout() {
        throw new UnsupportedOperationException("The fixture context has no project layout");
    }
}
