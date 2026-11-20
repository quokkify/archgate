package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A TestRail case field definition, reduced to what the automation type lookup needs.
 *
 * @param systemName field name as cases carry it, for example {@code custom_automation_type}
 * @param items      option list of the field's first configuration, one {@code "<id>, <name>"} per line, or
 *                   {@code null} when the field has no options
 */
record FieldRecord(String systemName, String items) {

    /**
     * Reads the response of {@code get_case_fields}.
     *
     * @param endpoint endpoint named in failure messages
     * @param json     parsed response body, a JSON array of field objects
     * @return the fields in response order
     */
    static List<FieldRecord> listFrom(String endpoint, Object json) {
        return JsonValues.array(endpoint, json).stream()
                .map(field -> JsonValues.object(endpoint, field))
                .map(FieldRecord::from)
                .toList();
    }

    private static FieldRecord from(Map<?, ?> field) {
        String items = null;
        if (field.get("configs") instanceof List<?> configs
                && !configs.isEmpty()
                && configs.get(0) instanceof Map<?, ?> config
                && config.get("options") instanceof Map<?, ?> options) {
            items = JsonValues.text(options.get("items")).orElse(null);
        }
        return new FieldRecord(JsonValues.text(field.get("system_name")).orElse(null), items);
    }

    boolean named(String name) {
        return Objects.equals(systemName, name);
    }
}
