package dev.quokkify.archgate.context.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;

public interface ArchUnitContext extends AnalysisContext {

    String rootPackage();

    JavaClasses main();

    JavaClasses test();

    JavaClasses all();

    default JavaClasses classes(Scope scope) {
        return switch (scope) {
            case MAIN -> main();
            case TEST -> test();
        };
    }

    ProjectLayout layout();
}
