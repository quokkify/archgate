package dev.quokkify.archgate.context.classgraph;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import io.github.classgraph.ScanResult;

public interface ClassGraphContext extends AnalysisContext, AutoCloseable {

    String rootPackage();

    ProjectLayout layout();

    ScanResult scan(Scope scope);

    @Override
    void close();
}
