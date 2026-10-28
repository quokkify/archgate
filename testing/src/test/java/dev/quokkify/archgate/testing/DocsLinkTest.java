package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocsLinkTest {

    private static final Path ROOT = Path.of(System.getProperty("archgate.root"));
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern RULE_ROW = Pattern.compile("^\\|\\s*`([A-Za-z0-9]+Rule)`");
    private static final Pattern EXTERNAL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final String FENCE = "```";

    @Test
    void everyRelativeLinkResolves() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path file : markdownFiles()) {
            for (String target : relativeLinks(file)) {
                Path resolved = file.getParent().resolve(target).normalize();
                if (!Files.exists(resolved)) {
                    broken.add(ROOT.relativize(file) + " -> " + target);
                }
            }
        }
        assertThat(broken).as("broken relative links").isEmpty();
    }

    @Test
    void everyRuleInTheReadmeTablesExists() throws IOException {
        Set<String> implemented = implementedRules();
        assertThat(readmeRules("README.md")).as("README.md").isNotEmpty().isSubsetOf(implemented);
    }

    @Test
    void everyImplementedRuleIsInTheReadmeTable() throws IOException {
        assertThat(readmeRules("README.md")).isEqualTo(implementedRules());
    }

    private static List<Path> markdownFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> readmes = Files.list(ROOT)) {
            readmes.filter(path -> path.getFileName().toString().matches("(README.*|AGENTS)\\.md"))
                    .forEach(files::add);
        }
        try (Stream<Path> docs = Files.walk(ROOT.resolve("docs"))) {
            docs.filter(path -> path.toString().endsWith(".md")).forEach(files::add);
        }
        return files;
    }

    private static List<String> relativeLinks(Path file) throws IOException {
        List<String> targets = new ArrayList<>();
        boolean fenced = false;
        for (String line : Files.readAllLines(file)) {
            if (line.stripLeading().startsWith(FENCE)) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            Matcher matcher = LINK.matcher(line);
            while (matcher.find()) {
                String target = matcher.group(1);
                int hash = target.indexOf('#');
                String path = hash >= 0 ? target.substring(0, hash) : target;
                if (!path.isEmpty() && !EXTERNAL.matcher(path).matches()) {
                    targets.add(path);
                }
            }
        }
        return targets;
    }

    private static Set<String> readmeRules(String readme) throws IOException {
        Set<String> rules = new TreeSet<>();
        for (String line : Files.readAllLines(ROOT.resolve(readme))) {
            Matcher matcher = RULE_ROW.matcher(line);
            if (matcher.find()) {
                rules.add(matcher.group(1));
            }
        }
        return rules;
    }

    private static Set<String> implementedRules() throws IOException {
        Set<String> rules = new TreeSet<>();
        try (Stream<Path> backends = Files.list(ROOT.resolve("backends"))) {
            for (Path backend : backends.toList()) {
                Path sources = backend.resolve("rules/src/main/java");
                if (Files.isDirectory(sources)) {
                    try (Stream<Path> files = Files.walk(sources)) {
                        files.map(path -> path.getFileName().toString())
                                .filter(name -> name.endsWith("Rule.java"))
                                .map(name -> name.substring(0, name.length() - ".java".length()))
                                .forEach(rules::add);
                    }
                }
            }
        }
        assertThat(rules).isNotEmpty();
        return rules;
    }
}
