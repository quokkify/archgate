package dev.quokkify.archgate.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GradleProjectWriter {

    static final String ARCHGATE_GROUP = "dev.quokkify.archgate";
    static final String PLUGIN_ID = "dev.quokkify.archgate";

    private static final String PUBLISHED_PLUGIN_MANAGEMENT = """
            pluginManagement {
                repositories {
                    maven { url = '%1$s' }
                    gradlePluginPortal()
                }
                plugins {
                    id '%2$s' version '%3$s'
                }
            }

            """;
    private static final String SETTINGS = """
            %6$sdependencyResolutionManagement {
                repositories {
                    maven { url = '%1$s' }
                    mavenCentral()
                }
            }

            rootProject.name = '%4$s'
            %5$s""";
    private static final String BUILD_HEADER = """
            plugins {
                id 'java-library'
            %s}

            group = 'com.example'
            version = '1.0'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }

            tasks.named('test') {
                failOnNoDiscoveredTests = false
            }
            """;

    private static final String ELEMENTS_ONLY_BLOCK = """

            archgate {
                skip = true
            }
            """;

    private GradleProjectWriter() {}

    public static void write(
            String scenario, FixtureSpec spec, Path target, Path itRepo, String archgateVersion, PluginMode mode) {
        writeFile(target.resolve("settings.gradle"), settings(scenario, spec, itRepo, archgateVersion, mode));
        if (spec.singleModule()) {
            writeFile(target.resolve("build.gradle"), buildFile(spec, null, archgateVersion, true, false));
            return;
        }
        String gate = spec.gate().orElse("");
        for (String module : spec.modules()) {
            boolean isGate = module.equals(gate);
            boolean exposesElements = spec.dependenciesOf(gate).contains(module);
            writeFile(
                    target.resolve(module).resolve("build.gradle"),
                    buildFile(spec, module, archgateVersion, isGate, exposesElements));
        }
    }

    static String settings(String scenario, FixtureSpec spec, Path itRepo, String archgateVersion, PluginMode mode) {
        String includes = spec.modules().stream()
                .map(module -> "include '" + module + "'\n")
                .collect(Collectors.joining());
        String pluginManagement = mode == PluginMode.PUBLISHED
                ? PUBLISHED_PLUGIN_MANAGEMENT.formatted(itRepo.toUri(), PLUGIN_ID, archgateVersion)
                : "";
        return SETTINGS.formatted(itRepo.toUri(), PLUGIN_ID, archgateVersion, scenario, includes, pluginManagement);
    }

    static String buildFile(
            FixtureSpec spec, String module, String archgateVersion, boolean isGate, boolean exposesElements) {
        boolean appliesPlugin = isGate || exposesElements;
        String pluginLine = appliesPlugin ? "    id '" + PLUGIN_ID + "'\n" : "";
        StringBuilder out = new StringBuilder(BUILD_HEADER.formatted(pluginLine));
        String ruleLibrary =
                Objects.isNull(module) ? null : spec.ruleLibraries().get(module);
        if (Objects.nonNull(ruleLibrary)) {
            out.append("""

                    base {
                        archivesName = '%s'
                    }

                    jar {
                        manifest {
                            attributes('Archgate-Api-Version': '%s')
                        }
                    }
                    """.formatted(module, ruleLibrary));
        }
        if (isGate) {
            out.append(archgateBlock(spec));
        } else if (exposesElements) {
            out.append(ELEMENTS_ONLY_BLOCK);
        }
        out.append(dependencies(spec, module, archgateVersion, isGate, Objects.nonNull(ruleLibrary)));
        return out.toString();
    }

    private static String archgateBlock(FixtureSpec spec) {
        String gate = spec.gate().orElse("");
        StringBuilder block = new StringBuilder("\narchgate {\n    rootPackage = '" + spec.rootPackage() + "'\n");
        if (!spec.dependenciesOf(gate).isEmpty()) {
            String modules = spec.dependenciesOf(gate).stream()
                    .map(module -> "':" + module + "'")
                    .collect(Collectors.joining(", "));
            block.append("    modules(").append(modules).append(")\n");
        }
        return block.append("}\n").toString();
    }

    private static String dependencies(
            FixtureSpec spec, String module, String archgateVersion, boolean isGate, boolean isRuleLibrary) {
        StringBuilder deps = new StringBuilder();
        if (Objects.nonNull(module)) {
            for (String sibling : spec.dependenciesOf(module)) {
                deps.append("    implementation project(':").append(sibling).append("')\n");
            }
        }
        if (isGate) {
            for (String library : spec.ruleLibraries().keySet()) {
                deps.append("    testImplementation project(':").append(library).append("')\n");
            }
            for (String artifact : spec.testDependencies()) {
                deps.append("    testImplementation '")
                        .append(ARCHGATE_GROUP)
                        .append(':')
                        .append(artifact)
                        .append(':')
                        .append(archgateVersion)
                        .append("'\n");
            }
        }
        if (isRuleLibrary) {
            deps.append("    compileOnly '")
                    .append(ARCHGATE_GROUP)
                    .append(":archgate-context-archunit:")
                    .append(archgateVersion)
                    .append("'\n");
        }
        return deps.length() == 0 ? "" : "\ndependencies {\n" + deps + "}\n";
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
