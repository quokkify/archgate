package dev.quokkify.archgate.context.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.Scope;
import java.util.List;

public interface JavaParserContext extends AnalysisContext {

    String rootPackage();

    List<CompilationUnit> compilationUnits(Scope scope);
}
