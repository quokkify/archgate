package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class RequiredAnnotationCondition extends ArchCondition<JavaClass> {

    private final RequiredAnnotation annotation;
    private final Set<String> excludedAnnotations;

    RequiredAnnotationCondition(RequiredAnnotation annotation, Set<String> excludedAnnotations) {
        super("carry @" + annotation.simpleName() + (annotation.nonBlankValue() ? " with a non-blank value" : "")
                + " on every test method");
        this.annotation = annotation;
        this.excludedAnnotations = Set.copyOf(excludedAnnotations);
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getMethods().stream()
                .filter(TestMethods::isTestMethod)
                .filter(method -> excludedAnnotations.stream().noneMatch(method::isAnnotatedWith))
                .forEach(method -> checkMethod(method, events));
    }

    private void checkMethod(JavaMethod method, ConditionEvents events) {
        List<JavaAnnotation<?>> found = MethodAnnotations.collect(method, annotation.name(), annotation.container());
        if (found.isEmpty()) {
            violated(
                    events,
                    method,
                    "Method <%s> is a test without @%s in %s",
                    method.getFullName(),
                    annotation.simpleName(),
                    method.getSourceCodeLocation());
        } else if (annotation.nonBlankValue()
                && found.stream().map(MethodAnnotations::nonBlankValue).allMatch(Optional::isEmpty)) {
            violated(
                    events,
                    method,
                    "Method <%s> has a blank @%s in %s",
                    method.getFullName(),
                    annotation.simpleName(),
                    method.getSourceCodeLocation());
        }
    }

    private static void violated(ConditionEvents events, Object item, String format, Object... args) {
        events.add(SimpleConditionEvent.violated(item, format.formatted(args)));
    }
}
