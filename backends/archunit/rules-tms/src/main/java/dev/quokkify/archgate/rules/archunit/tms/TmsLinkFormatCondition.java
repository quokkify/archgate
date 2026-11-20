package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.regex.Pattern;

final class TmsLinkFormatCondition extends ArchCondition<JavaClass> {

    private static final char CYRILLIC_UPPER_ES = '\u0421'; // Cyrillic capital Es
    private static final char CYRILLIC_LOWER_ES = '\u0441'; // Cyrillic small es
    private static final String CYRILLIC_HINT = ": Cyrillic '\u0421' found, use the Latin 'C'"; // Cyrillic capital Es

    private final Pattern format;
    private final Set<String> excludedAnnotations;

    TmsLinkFormatCondition(Pattern format, Set<String> excludedAnnotations) {
        super("carry @TmsLink values matching %s".formatted(format.pattern()));
        this.format = format;
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
        for (JavaAnnotation<?> link : TmsLinks.annotations(method)) {
            link.get("value")
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .filter(value -> !format.matcher(value).matches())
                    .ifPresent(value -> events.add(SimpleConditionEvent.violated(method, describe(method, value))));
        }
    }

    private static String describe(JavaMethod method, String value) {
        String message = "Method <%s> has a malformed @TmsLink \"%s\" in %s"
                .formatted(method.getFullName(), value, method.getSourceCodeLocation());
        boolean cyrillic = value.indexOf(CYRILLIC_UPPER_ES) >= 0 || value.indexOf(CYRILLIC_LOWER_ES) >= 0;
        return cyrillic ? message + CYRILLIC_HINT : message;
    }
}
