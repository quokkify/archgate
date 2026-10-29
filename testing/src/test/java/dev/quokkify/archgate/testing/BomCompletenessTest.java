package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

class BomCompletenessTest {

    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern MANAGED =
            Pattern.compile("<dependencyManagement>(.*?)</dependencyManagement>", Pattern.DOTALL);
    private static final Set<String> EXPECTED = Set.of(
            "archgate-api",
            "archgate-core",
            "archgate-context-archunit",
            "archgate-context-javaparser",
            "archgate-context-classgraph",
            "archgate-rules-archunit",
            "archgate-rules-javaparser",
            "archgate-rules-classgraph",
            "archgate-maven-plugin",
            "archgate-gradle-plugin");

    @Test
    void bomManagesExactlyTheOtherPublishedArtifacts() throws IOException {
        String pom = Files.readString(Path.of(System.getProperty("archgate.bomPom")));
        Matcher managed = MANAGED.matcher(pom);
        assertThat(managed.find()).as("dependencyManagement block").isTrue();
        Set<String> listed = new TreeSet<>();
        Matcher artifact = ARTIFACT_ID.matcher(managed.group(1));
        while (artifact.find()) {
            listed.add(artifact.group(1));
        }
        assertThat(listed).isEqualTo(new TreeSet<>(EXPECTED));
    }
}
