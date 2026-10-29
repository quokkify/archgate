package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testsupport.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

class MavenProjectWriterTest {

    @Test
    void gatePomBindsCheckGoal() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project = Fixture.materialize(
                    "multi-module-violation", BuildTool.MAVEN, work.resolve("p"), work.resolve("repo"), "1.2.3");

            String gate = Files.readString(project.resolve("architecture/pom.xml"));
            assertThat(gate)
                    .contains("<artifactId>archgate-maven-plugin</artifactId>")
                    .contains("<version>1.2.3</version>")
                    .contains("<goal>check</goal>")
                    .contains("<rootPackage>com.example</rootPackage>")
                    .contains("<artifactId>archgate-rules-archunit</artifactId>");
            assertThat(Files.readString(project.resolve("pom.xml")))
                    .contains("<packaging>pom</packaging>")
                    .contains("<module>api</module>");
            assertThat(Files.readString(project.resolve("api/pom.xml"))).doesNotContain("archgate-maven-plugin");
            assertThat(Files.readString(project.resolve("settings.xml")))
                    .contains("<id>it-local</id>")
                    .contains(work.resolve("repo").toUri().toString());
            assertThat(project.resolve("api/src/main/java/com/example/api/Account.java"))
                    .exists();
            assertThat(project.resolve("fixture.properties")).doesNotExist();
        }
    }

    @Test
    void ruleLibraryJarCarriesManifestAndGateDependsOnIt() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project = Fixture.materialize(
                    "incompatible-rule-library", BuildTool.MAVEN, work.resolve("p"), work.resolve("repo"), "1.2.3");

            assertThat(Files.readString(project.resolve("rules-lib/pom.xml")))
                    .contains("<Archgate-Api-Version>not-a-version</Archgate-Api-Version>");
            assertThat(Files.readString(project.resolve("architecture/pom.xml")))
                    .contains("<artifactId>rules-lib</artifactId>");
        }
    }

    @Test
    void singleModuleProjectHasPluginInRootPom() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project =
                    Fixture.materialize("pass", BuildTool.MAVEN, work.resolve("p"), work.resolve("repo"), "1.2.3");

            assertThat(Files.readString(project.resolve("pom.xml")))
                    .contains("<artifactId>pass</artifactId>")
                    .contains("<goal>check</goal>")
                    .doesNotContain("<packaging>pom</packaging>");
        }
    }
}
