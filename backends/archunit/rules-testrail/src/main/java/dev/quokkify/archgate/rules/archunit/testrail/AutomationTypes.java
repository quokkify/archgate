package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads the automation type options that TestRail defines on a case field, by default {@code custom_automation_type}. */
final class AutomationTypes {

    private AutomationTypes() {}

    /**
     * Maps each automation type id of the default field to its name.
     *
     * @param fields case field definitions from {@code get_case_fields}
     * @return names by id, in option order
     * @throws IllegalStateException if the field is missing, has no options, an option is not {@code "<id>, <name>"}
     *     or an id repeats
     */
    static Map<Integer, String> parse(List<FieldRecord> fields) {
        return parse(fields, CaseRecord.AUTOMATION_TYPE_FIELD);
    }

    /**
     * Maps each automation type id of the named field to its name.
     *
     * @param fields case field definitions from {@code get_case_fields}
     * @param field system name of the field holding the automation type
     * @return names by id, in option order
     * @throws IllegalStateException if the field is missing, has no options, an option is not {@code "<id>, <name>"}
     *     or an id repeats
     */
    static Map<Integer, String> parse(List<FieldRecord> fields, String field) {
        FieldRecord automationType = fields.stream()
                .filter(candidate -> candidate.named(field))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("TestRail case field %s not found".formatted(field)));
        if (Objects.isNull(automationType.items())) {
            throw new IllegalStateException("TestRail case field %s has no options".formatted(field));
        }
        Map<Integer, String> names = new LinkedHashMap<>();
        for (String line : automationType.items().split("\n")) {
            String option = line.strip();
            if (option.isEmpty()) {
                continue;
            }
            String[] idAndName = option.split(",", 2);
            try {
                if (idAndName.length < 2) {
                    throw new NumberFormatException("no name");
                }
                int id = Integer.parseInt(idAndName[0].strip());
                if (names.putIfAbsent(id, idAndName[1].strip()) != null) {
                    throw new IllegalStateException(
                            "TestRail case field %s has a duplicate option id %d".formatted(field, id));
                }
            } catch (NumberFormatException unreadable) {
                throw new IllegalStateException(
                        "TestRail case field %s has an unreadable option '%s'".formatted(field, option), unreadable);
            }
        }
        return Collections.unmodifiableMap(names);
    }
}
