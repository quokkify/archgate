import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

final class FixtureTests {

    private FixtureTests() {
    }

    static void configure(Test task, Project project, boolean withMaven) {
        Project root = project.rootProject
        task.dependsOn root.tasks.named('publishToItRepo')
        task.inputs.files(root.itRepoArtifacts).withPropertyName('itRepoArtifacts').withPathSensitivity(PathSensitivity.NAME_ONLY)
        task.inputs.dir(root.file('fixtures')).withPropertyName('fixtures')
        task.systemProperty 'archgate.itRepo', root.layout.buildDirectory.dir('it-repo').get().asFile.path
        task.systemProperty 'archgate.fixtures', root.file('fixtures').path
        task.systemProperty 'archgate.version', project.version.toString()
        if (withMaven) {
            String mavenRuntime = project.extensions.getByType(VersionCatalogsExtension).named('libs')
                    .findVersion('mavenRuntime').get().requiredVersion
            task.dependsOn ':maven-plugin:unpackMaven'
            task.systemProperty 'maven.home', project.project(':maven-plugin').layout.buildDirectory
                    .dir("maven/apache-maven-${mavenRuntime}").get().asFile.path
            task.systemProperty 'archgate.mavenCache', root.mavenItCache.get().asFile.path
        }
        task.testLogging {
            events 'failed'
            exceptionFormat = 'full'
        }
    }
}
