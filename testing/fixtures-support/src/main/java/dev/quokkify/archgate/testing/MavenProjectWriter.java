package dev.quokkify.archgate.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MavenProjectWriter {

    static final String GROUP_ID = "com.example";
    static final String VERSION = "1.0";
    static final String ARCHGATE_GROUP = "dev.quokkify.archgate";
    static final String PLUGIN_ARTIFACT = "archgate-maven-plugin";

    private static final String POM_OPEN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

            """;
    private static final String PROPERTIES = """

                <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>
            """;
    private static final String COORDINATES = """
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
            """;
    private static final String PARENT = """
                <parent>
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                </parent>

                <artifactId>%s</artifactId>
            """;
    private static final String DEPENDENCY = """
                    <dependency>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
            %s        </dependency>
            """;
    private static final String CHECK_PLUGIN = """
                        <plugin>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                            <configuration>
                                <rootPackage>%s</rootPackage>
                            </configuration>
                            <executions>
                                <execution>
                                    <goals>
                                        <goal>check</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
            """;
    private static final String MANIFEST_PLUGIN = """
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-jar-plugin</artifactId>
                            <configuration>
                                <archive>
                                    <manifestEntries>
                                        <Archgate-Api-Version>%s</Archgate-Api-Version>
                                    </manifestEntries>
                                </archive>
                            </configuration>
                        </plugin>
            """;
    private static final String REPOSITORY = """
                            <%1$s>
                                <id>it-local</id>
                                <url>%2$s</url>
                                <releases>
                                    <enabled>true</enabled>
                                </releases>
                                <snapshots>
                                    <enabled>true</enabled>
                                </snapshots>
                            </%1$s>
            """;
    private static final String SETTINGS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <settings>
                <profiles>
                    <profile>
                        <id>it-repo</id>
                        <repositories>
            %s            </repositories>
                        <pluginRepositories>
            %s            </pluginRepositories>
                    </profile>
                </profiles>
                <activeProfiles>
                    <activeProfile>it-repo</activeProfile>
                </activeProfiles>
            </settings>
            """;

    private MavenProjectWriter() {}

    public static void write(String scenario, FixtureSpec spec, Path target, Path itRepo, String archgateVersion) {
        writeFile(target.resolve("settings.xml"), settings(itRepo));
        if (spec.singleModule()) {
            writeFile(target.resolve("pom.xml"), modulePom(scenario, spec, null, scenario, archgateVersion, true));
            return;
        }
        writeFile(target.resolve("pom.xml"), parentPom(scenario, spec));
        for (String module : spec.modules()) {
            boolean isGate = spec.gate().filter(module::equals).isPresent();
            writeFile(
                    target.resolve(module).resolve("pom.xml"),
                    modulePom(scenario, spec, scenario, module, archgateVersion, isGate));
        }
    }

    static String parentPom(String scenario, FixtureSpec spec) {
        String modules = spec.modules().stream()
                .map(module -> "        <module>" + module + "</module>\n")
                .collect(Collectors.joining());
        return POM_OPEN
                + COORDINATES.formatted(GROUP_ID, scenario, VERSION)
                + "    <packaging>pom</packaging>\n"
                + PROPERTIES
                + "\n    <modules>\n"
                + modules
                + "    </modules>\n</project>\n";
    }

    static String modulePom(
            String scenario,
            FixtureSpec spec,
            String parent,
            String artifactId,
            String archgateVersion,
            boolean isGate) {
        StringBuilder pom = new StringBuilder(POM_OPEN);
        if (Objects.isNull(parent)) {
            pom.append(COORDINATES.formatted(GROUP_ID, artifactId, VERSION)).append(PROPERTIES);
        } else {
            pom.append(PARENT.formatted(GROUP_ID, parent, VERSION, artifactId));
        }
        String dependencies = dependencies(spec, artifactId, archgateVersion, isGate);
        if (!dependencies.isEmpty()) {
            pom.append("\n    <dependencies>\n").append(dependencies).append("    </dependencies>\n");
        }
        String plugins = plugins(spec, artifactId, archgateVersion, isGate);
        if (!plugins.isEmpty()) {
            pom.append("\n    <build>\n        <plugins>\n")
                    .append(plugins)
                    .append("        </plugins>\n    </build>\n");
        }
        return pom.append("</project>\n").toString();
    }

    private static String dependencies(FixtureSpec spec, String module, String archgateVersion, boolean isGate) {
        StringBuilder out = new StringBuilder();
        for (String sibling : spec.dependenciesOf(module)) {
            out.append(dependency(GROUP_ID, sibling, VERSION, null));
        }
        if (isGate) {
            for (String library : spec.ruleLibraries().keySet()) {
                out.append(dependency(GROUP_ID, library, VERSION, "test"));
            }
            for (String artifact : spec.testDependencies()) {
                out.append(dependency(ARCHGATE_GROUP, artifact, archgateVersion, "test"));
            }
        }
        if (spec.ruleLibraries().containsKey(module)) {
            out.append(dependency(ARCHGATE_GROUP, "archgate-context-archunit", archgateVersion, "provided"));
        }
        return out.toString();
    }

    private static String dependency(String group, String artifact, String version, String scope) {
        String scopeLine = Objects.isNull(scope) ? "" : "            <scope>" + scope + "</scope>\n";
        return DEPENDENCY.formatted(group, artifact, version, scopeLine);
    }

    private static String plugins(FixtureSpec spec, String module, String archgateVersion, boolean isGate) {
        StringBuilder out = new StringBuilder();
        if (isGate) {
            out.append(CHECK_PLUGIN.formatted(ARCHGATE_GROUP, PLUGIN_ARTIFACT, archgateVersion, spec.rootPackage()));
        }
        if (spec.ruleLibraries().containsKey(module)) {
            out.append(MANIFEST_PLUGIN.formatted(spec.ruleLibraries().get(module)));
        }
        return out.toString();
    }

    static String settings(Path itRepo) {
        String url = itRepo.toUri().toString();
        return SETTINGS.formatted(
                REPOSITORY.formatted("repository", url), REPOSITORY.formatted("pluginRepository", url));
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
