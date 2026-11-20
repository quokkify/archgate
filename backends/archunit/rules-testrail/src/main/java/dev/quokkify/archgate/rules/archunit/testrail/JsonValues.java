package dev.quokkify.archgate.rules.archunit.testrail;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed reads of values produced by {@link Json}; every failure names the endpoint that returned the value. */
final class JsonValues {

    private JsonValues() {}

    static Map<?, ?> object(String endpoint, Object value) {
        if (value instanceof Map<?, ?> object) {
            return object;
        }
        throw new IllegalStateException("TestRail %s response is not a JSON object".formatted(endpoint));
    }

    static List<?> array(String endpoint, Object value) {
        if (value instanceof List<?> array) {
            return array;
        }
        throw new IllegalStateException("TestRail %s response is not a JSON array".formatted(endpoint));
    }

    static Optional<Integer> integer(Object value) {
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            return Optional.of(number.intValue());
        }
        if (value instanceof BigDecimal number) {
            try {
                return Optional.of(number.intValueExact());
            } catch (ArithmeticException notAnInteger) {
                return Optional.empty();
            }
        }
        if (value instanceof String digits && !digits.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(digits.strip()));
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static int requiredInteger(String endpoint, Map<?, ?> object, String name) {
        return integer(object.get(name))
                .orElseThrow(() ->
                        new IllegalStateException("TestRail %s response has no integer %s".formatted(endpoint, name)));
    }

    static Optional<String> text(Object value) {
        return value instanceof String string ? Optional.of(string) : Optional.empty();
    }
}
