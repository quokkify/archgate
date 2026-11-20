package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class MethodAnnotations {

    private static final String VALUE = "value";

    private MethodAnnotations() {}

    static List<JavaAnnotation<?>> collect(JavaMethod method, String annotationName, Optional<String> containerName) {
        List<JavaAnnotation<?>> found = new ArrayList<>();
        method.tryGetAnnotationOfType(annotationName).ifPresent(found::add);
        containerName
                .flatMap(method::tryGetAnnotationOfType)
                .flatMap(container -> container.get(VALUE))
                .filter(JavaAnnotation[].class::isInstance)
                .map(JavaAnnotation[].class::cast)
                .ifPresent(nested -> Arrays.stream(nested).forEach(found::add));
        return found;
    }

    static Optional<String> nonBlankValue(JavaAnnotation<?> annotation) {
        return annotation
                .get(VALUE)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank());
    }
}
