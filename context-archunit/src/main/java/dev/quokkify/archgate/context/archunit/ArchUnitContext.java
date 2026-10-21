package dev.quokkify.archgate.context.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ProjectLayout;

public interface ArchUnitContext extends AnalysisContext {

    String rootPackage();

    JavaClasses main();

    JavaClasses test();

    JavaClasses all();

    ProjectLayout layout();
}
