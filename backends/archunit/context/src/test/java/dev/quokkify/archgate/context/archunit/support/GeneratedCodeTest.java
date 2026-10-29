package dev.quokkify.archgate.context.archunit.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.testng.annotations.Test;

class GeneratedCodeTest {

    @Test
    void generatedQueryClassesAreExcluded() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("archfixtures.generated");

        assertThat(fixtures.stream().filter(GeneratedCode.EXCLUDED).map(JavaClass::getName))
                .containsExactly("archfixtures.generated.QFoo");
    }
}
