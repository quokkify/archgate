package dev.quokkify.archgate.cases.archunit;

import java.util.Objects;
import java.util.Set;

/** Argument checks shared by the rule constructors. */
final class Rules {

    private Rules() {}

    static String displayName(CaseService service) {
        Objects.requireNonNull(service, "service");
        String name = Objects.requireNonNull(service.displayName(), "service.displayName()");
        if (name.isBlank()) {
            throw new IllegalArgumentException(service.getClass().getName() + " has a blank display name");
        }
        return name;
    }

    static Set<String> typeNames(Set<String> names, String kind) {
        Set<String> copy = Set.copyOf(names);
        if (copy.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("A " + kind + " type name must not be blank");
        }
        return copy;
    }
}
