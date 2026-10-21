package dev.quokkify.archgate.rules.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.javaparser.JavaParserContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NoWildcardImportsRule implements ArchitectureRule<JavaParserContext> {

    private static final String EXPECTED_CONTRACT = """
      Imports must name the type. Replace each 'import x.y.*' with the types it uses; static wildcard imports \
      are allowed.""";

    @Override
    public String name() {
        return "No wildcard imports";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    @Override
    public Class<JavaParserContext> contextType() {
        return JavaParserContext.class;
    }

    @Override
    public void verify(JavaParserContext context) {
        List<String> violations = new ArrayList<>();
        for (Scope scope : List.of(Scope.MAIN, Scope.TEST)) {
            for (CompilationUnit unit : context.compilationUnits(scope)) {
                String location = location(unit);
                for (ImportDeclaration declaration : unit.getImports()) {
                    if (declaration.isAsterisk() && !declaration.isStatic()) {
                        int line = declaration
                                .getBegin()
                                .map(position -> position.line)
                                .orElse(0);
                        violations.add(location + ":" + line + " import " + declaration.getNameAsString() + ".*");
                    }
                }
            }
        }
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    private static String location(CompilationUnit unit) {
        return unit.getStorage()
                .map(storage -> {
                    Path file = storage.getPath();
                    Path relative = relativeToSourceRoot(storage.getSourceRoot(), file);
                    return relative.toString().replace('\\', '/');
                })
                .orElse("<unknown>");
    }

    private static Path relativeToSourceRoot(Path sourceRoot, Path file) {
        return file.startsWith(sourceRoot) ? sourceRoot.relativize(file) : file.getFileName();
    }
}
