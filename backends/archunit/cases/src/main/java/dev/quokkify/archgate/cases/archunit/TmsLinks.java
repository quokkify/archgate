package dev.quokkify.archgate.cases.archunit;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class TmsLinks {

    private static final String TMS_LINK = "io.qameta.allure.TmsLink";
    private static final String TMS_LINKS = "io.qameta.allure.TmsLinks";
    private static final String VALUE = "value";

    private TmsLinks() {}

    /** The raw values of the method's {@code @TmsLink} annotations, direct and inside {@code @TmsLinks}. */
    static List<String> values(JavaMethod method) {
        List<JavaAnnotation<?>> links = new ArrayList<>();
        method.tryGetAnnotationOfType(TMS_LINK).ifPresent(links::add);
        method.tryGetAnnotationOfType(TMS_LINKS)
                .flatMap(container -> container.get(VALUE))
                .filter(JavaAnnotation[].class::isInstance)
                .map(JavaAnnotation[].class::cast)
                .ifPresent(nested -> Arrays.stream(nested).forEach(links::add));
        return links.stream()
                .map(link -> link.get(VALUE))
                .flatMap(Optional::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
