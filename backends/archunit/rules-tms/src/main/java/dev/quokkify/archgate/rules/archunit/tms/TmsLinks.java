package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class TmsLinks {

    private static final String TMS_LINK = "io.qameta.allure.TmsLink";
    private static final String TMS_LINKS = "io.qameta.allure.TmsLinks";

    private TmsLinks() {}

    static List<JavaAnnotation<?>> annotations(JavaMethod method) {
        return MethodAnnotations.collect(method, TMS_LINK, Optional.of(TMS_LINKS));
    }

    static Optional<String> value(JavaAnnotation<?> link) {
        return MethodAnnotations.nonBlankValue(link);
    }

    static Set<String> ids(JavaMethod method) {
        return annotations(method).stream()
                .map(TmsLinks::value)
                .flatMap(Optional::stream)
                .map(String::strip)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
