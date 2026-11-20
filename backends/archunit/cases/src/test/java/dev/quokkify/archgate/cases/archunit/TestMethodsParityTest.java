package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.testng.annotations.Test;

public class TestMethodsParityTest {

    private static final String FIXTURES = "archfixtures.tmslink";
    private static final String EXPECTED = "/archfixtures/tmslink/expected-test-methods.txt";

    @Test(description = "Regression guard: test-method detection stays identical to the copy in the TmsLink rules")
    public void detectsExpectedTestMethods() throws IOException {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES);
        List<String> actual = classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(TestMethods::isTestMethod)
                .map(JavaMethod::getFullName)
                .sorted()
                .toList();

        assertThat(actual).isEqualTo(expected());
    }

    private static List<String> expected() throws IOException {
        try (InputStream stream = TestMethodsParityTest.class.getResourceAsStream(EXPECTED)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }
}
