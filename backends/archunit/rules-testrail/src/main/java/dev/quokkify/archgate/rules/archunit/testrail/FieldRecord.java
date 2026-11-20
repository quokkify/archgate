package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A TestRail case field definition, reduced to what the automation type lookup needs.
 *
 * @param systemName field name as cases carry it, for example {@code custom_automation_type}
 * @param items      option list of the field's configuration for the project, one {@code "<id>, <name>"} per line,
 *                   or {@code null} when no configuration applies to the project or it has no options
 */
record FieldRecord(String systemName, String items) {

    /**
     * Reads the response of {@code get_case_fields}. TestRail can configure a field differently per project; the
     * configuration that lists the project wins, then a global one. A configuration without a context counts as global.
     *
     * @param endpoint  endpoint named in failure messages
     * @param json      parsed response body, a JSON array of field objects
     * @param projectId project whose field configuration applies
     * @return the fields in response order
     */
    static List<FieldRecord> listFrom(String endpoint, Object json, int projectId) {
        return JsonValues.array(endpoint, json).stream()
                .map(field -> JsonValues.object(endpoint, field))
                .map(field -> from(field, projectId))
                .toList();
    }

    private static FieldRecord from(Map<?, ?> field, int projectId) {
        List<Map<?, ?>> configs = field.get("configs") instanceof List<?> all
                ? all.stream()
                        .filter(Map.class::isInstance)
                        .<Map<?, ?>>map(Map.class::cast)
                        .toList()
                : List.of();
        String items = configs.stream()
                .filter(config -> listsProject(config, projectId))
                .findFirst()
                .or(() -> configs.stream().filter(FieldRecord::isGlobal).findFirst())
                .flatMap(FieldRecord::items)
                .orElse(null);
        return new FieldRecord(JsonValues.text(field.get("system_name")).orElse(null), items);
    }

    private static boolean listsProject(Map<?, ?> config, int projectId) {
        return config.get("context") instanceof Map<?, ?> context
                && context.get("project_ids") instanceof List<?> projectIds
                && projectIds.stream()
                        .map(JsonValues::integer)
                        .anyMatch(id -> id.filter(value -> value == projectId).isPresent());
    }

    private static boolean isGlobal(Map<?, ?> config) {
        return !(config.get("context") instanceof Map<?, ?> context) || Boolean.TRUE.equals(context.get("is_global"));
    }

    private static Optional<String> items(Map<?, ?> config) {
        return config.get("options") instanceof Map<?, ?> options
                ? JsonValues.text(options.get("items"))
                : Optional.empty();
    }

    boolean named(String name) {
        return Objects.equals(systemName, name);
    }
}
