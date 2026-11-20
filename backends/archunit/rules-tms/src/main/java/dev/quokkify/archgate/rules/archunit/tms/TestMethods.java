package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class TestMethods {

    private static final String TESTNG_ANNOTATION = "org.testng.annotations.%s";
    private static final Set<String> TEST_ANNOTATIONS = Set.of(TESTNG_ANNOTATION.formatted("Test"));
    private static final List<String> NON_TEST_ANNOTATIONS = Stream.of(
                    "BeforeSuite",
                    "AfterSuite",
                    "BeforeTest",
                    "AfterTest",
                    "BeforeGroups",
                    "AfterGroups",
                    "BeforeClass",
                    "AfterClass",
                    "BeforeMethod",
                    "AfterMethod",
                    "Factory",
                    "DataProvider")
            .map(TESTNG_ANNOTATION::formatted)
            .toList();

    private TestMethods() {}

    static boolean isTestMethod(JavaMethod method) {
        return isAnnotatedAsTest(method) || isClassLevelTestMethod(method);
    }

    private static boolean isClassLevelTestMethod(JavaMethod method) {
        Set<JavaModifier> modifiers = method.getModifiers();
        return hasClassLevelTest(method.getOwner())
                && modifiers.contains(JavaModifier.PUBLIC)
                && !modifiers.contains(JavaModifier.STATIC)
                && !modifiers.contains(JavaModifier.SYNTHETIC)
                && !modifiers.contains(JavaModifier.BRIDGE)
                && method.getRawReturnType().isEquivalentTo(void.class)
                && NON_TEST_ANNOTATIONS.stream().noneMatch(method::isAnnotatedWith);
    }

    private static boolean hasClassLevelTest(JavaClass javaClass) {
        return isAnnotatedAsTest(javaClass)
                || javaClass.getAllRawSuperclasses().stream().anyMatch(TestMethods::isAnnotatedAsTest);
    }

    private static boolean isAnnotatedAsTest(CanBeAnnotated element) {
        return TEST_ANNOTATIONS.stream().anyMatch(element::isAnnotatedWith);
    }
}
