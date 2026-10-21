package dev.quokkify.archgate.context.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

final class DirectoryJavaParserContext implements JavaParserContext {

    private static final String JAVA_SUFFIX = ".java";
    private static final String BUILD_TIME_PREFIX = "javaparser ";

    private final Map<String, Duration> buildTimes = new ConcurrentSkipListMap<>();
    private final Map<Scope, List<CompilationUnit>> parsed = new EnumMap<>(Scope.class);

    private final ProjectLayout layout;
    private final String rootPackage;

    DirectoryJavaParserContext(ProjectLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.rootPackage = Objects.requireNonNull(layout.rootPackage(), "rootPackage");
    }

    @Override
    public String rootPackage() {
        return rootPackage;
    }

    @Override
    public synchronized List<CompilationUnit> compilationUnits(Scope scope) {
        List<CompilationUnit> units = parsed.get(scope);
        if (Objects.isNull(units)) {
            long startedAt = System.nanoTime();
            units = parse(scope);
            buildTimes.put(
                    BUILD_TIME_PREFIX + scope.name().toLowerCase(Locale.ROOT),
                    Duration.ofNanos(System.nanoTime() - startedAt));
            parsed.put(scope, units);
        }
        return units;
    }

    @Override
    public Map<String, Duration> buildTimes() {
        return Collections.unmodifiableMap(new TreeMap<>(buildTimes));
    }

    private List<CompilationUnit> parse(Scope scope) {
        List<Path> configured = List.copyOf(layout.sourceDirs(scope));
        ParserConfiguration configuration =
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        JavaParser parser = new JavaParser(configuration);
        List<CompilationUnit> units = configured.stream()
                .filter(Files::isDirectory)
                .flatMap(DirectoryJavaParserContext::javaFiles)
                .map(file -> parseFile(parser, file))
                .filter(this::inRootPackage)
                .toList();
        if (units.isEmpty()) {
            throw new UnableToVerifyError(
                    "No Java source under %s was found in the %s source directories %s. Make the sources available, otherwise every rule using this view would pass without checking anything."
                            .formatted(rootPackage, scope.name().toLowerCase(Locale.ROOT), configured));
        }
        return List.copyOf(units);
    }

    private static Stream<Path> javaFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JAVA_SUFFIX))
                    .sorted()
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + dir, e);
        }
    }

    private static CompilationUnit parseFile(JavaParser parser, Path file) {
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(file);
        } catch (IOException e) {
            throw new UnableToVerifyError("Cannot parse " + file + ": " + e.getMessage(), e);
        }
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String problem = result.getProblems().isEmpty()
                    ? "no compilation unit produced"
                    : result.getProblems().get(0).getVerboseMessage();
            throw new UnableToVerifyError("Cannot parse " + file + ": " + problem);
        }
        return result.getResult().get();
    }

    private boolean inRootPackage(CompilationUnit unit) {
        return unit.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .filter(name -> name.equals(rootPackage) || name.startsWith(rootPackage + "."))
                .isPresent();
    }
}
