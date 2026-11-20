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
        return !isCompilerGenerated(method) && (isAnnotatedAsTest(method) || isClassLevelTestMethod(method));
    }

    private static boolean isCompilerGenerated(JavaMethod method) {
        Set<JavaModifier> modifiers = method.getModifiers();
        return modifiers.contains(JavaModifier.SYNTHETIC) || modifiers.contains(JavaModifier.BRIDGE);
    }

    private static boolean isClassLevelTestMethod(JavaMethod method) {
        Set<JavaModifier> modifiers = method.getModifiers();
        return hasClassLevelTest(method.getOwner())
                && modifiers.contains(JavaModifier.PUBLIC)
                && !isMainEntryPoint(method)
                && method.getRawReturnType().isEquivalentTo(void.class)
                && NON_TEST_ANNOTATIONS.stream().noneMatch(method::isAnnotatedWith);
    }

    /** TestNG leaves out {@code public static void main(String[])} but runs every other public static method. */
    private static boolean isMainEntryPoint(JavaMethod method) {
        return method.getModifiers().contains(JavaModifier.STATIC)
                && method.getName().equals("main")
                && method.getRawParameterTypes().size() == 1
                && method.getRawParameterTypes().get(0).isEquivalentTo(String[].class);
    }

    private static boolean hasClassLevelTest(JavaClass javaClass) {
        return isAnnotatedAsTest(javaClass)
                || javaClass.getAllRawSuperclasses().stream().anyMatch(TestMethods::isAnnotatedAsTest);
    }

    private static boolean isAnnotatedAsTest(CanBeAnnotated element) {
        return TEST_ANNOTATIONS.stream().anyMatch(element::isAnnotatedWith);
    }
}
