package dev.quokkify.archgate.rules.archunit.api;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports every way a class holds, builds or mutates a Jackson tree. Jackson types are named as strings, so this module
 * needs no Jackson on its classpath; the checked project brings its own.
 *
 * <p>Reading a node in place (a local variable, {@code path}, {@code get}, {@code asText}) is not reported. Storing a
 * node in a field, passing or returning it, subclassing a node type, naming one as a class literal or type argument,
 * creating nodes, mutating them, and the tree-producing methods of mappers and parsers are.
 */
final class JacksonTreeUsageCondition extends ArchCondition<JavaClass> {

    private static final String JSON_NODE = "com.fasterxml.jackson.databind.JsonNode";
    private static final String NODE_FACTORY = "com.fasterxml.jackson.databind.node.JsonNodeFactory";
    private static final List<String> TREE_CODECS =
            List.of("com.fasterxml.jackson.core.TreeCodec", "com.fasterxml.jackson.core.JsonParser");
    private static final Set<String> TREE_FACTORY_METHODS =
            Set.of("readTree", "valueToTree", "createObjectNode", "createArrayNode", "readValueAsTree");
    private static final List<String> MUTATOR_PREFIXES =
            List.of("put", "set", "add", "insert", "remove", "replace", "retain", "with");
    private static final Set<String> NODE_CREATORS = Set.of("<init>", "valueOf", "getInstance");

    JacksonTreeUsageCondition() {
        super("not use the Jackson tree API");
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.isAssignableTo(JSON_NODE)) {
            violated(
                    events,
                    javaClass,
                    "%s extends Jackson tree type %s in %s",
                    javaClass.getName(),
                    JSON_NODE,
                    javaClass.getSourceCodeLocation());
        }
        Stream.concat(javaClass.getSuperclass().stream(), javaClass.getInterfaces().stream())
                .forEach(supertype -> treeTypeArgumentsIn(supertype)
                        .forEach(type -> violated(
                                events,
                                javaClass,
                                "%s has supertype %s with type argument %s in %s",
                                javaClass.getName(),
                                supertype.getName(),
                                type.getName(),
                                javaClass.getSourceCodeLocation())));
        javaClass.getFields().forEach(field -> checkField(field, events));
        javaClass.getCodeUnits().forEach(codeUnit -> checkCodeUnit(codeUnit, events));
    }

    private static void checkField(JavaField field, ConditionEvents events) {
        treeTypeIn(field.getType())
                .forEach(type -> violated(
                        events,
                        field,
                        "Field <%s> has type %s in %s",
                        field.getFullName(),
                        type.getName(),
                        field.getOwner().getSourceCodeLocation()));
    }

    private static void checkCodeUnit(JavaCodeUnit codeUnit, ConditionEvents events) {
        if (!isCompilerGenerated(codeUnit)) {
            codeUnit.getParameterTypes()
                    .forEach(parameter -> treeTypeIn(parameter)
                            .forEach(type -> violated(
                                    events,
                                    codeUnit,
                                    "<%s> has parameter of type %s in %s",
                                    codeUnit.getFullName(),
                                    type.getName(),
                                    codeUnit.getSourceCodeLocation())));
            treeTypeIn(codeUnit.getReturnType())
                    .forEach(type -> violated(
                            events,
                            codeUnit,
                            "<%s> returns %s in %s",
                            codeUnit.getFullName(),
                            type.getName(),
                            codeUnit.getSourceCodeLocation()));
        }
        codeUnit.getAccessesFromSelf().stream()
                .filter(JacksonTreeUsageCondition::buildsOrMutatesTree)
                .forEach(access -> violated(events, access, "%s", access.getDescription()));
        codeUnit.getReferencedClassObjects().stream()
                .filter(reference -> isTreeType(reference.getRawType().getBaseComponentType()))
                .forEach(reference -> violated(
                        events,
                        reference,
                        "<%s> references %s.class in %s",
                        codeUnit.getFullName(),
                        sourceName(reference.getRawType()),
                        reference.getSourceCodeLocation()));
    }

    private static boolean buildsOrMutatesTree(JavaAccess<?> access) {
        JavaClass owner = access.getTargetOwner();
        String name = access.getName();
        if (owner.isAssignableTo(NODE_FACTORY)) {
            return true;
        }
        if (owner.isAssignableTo(JSON_NODE)) {
            return access instanceof JavaFieldAccess
                    || NODE_CREATORS.contains(name)
                    || MUTATOR_PREFIXES.stream().anyMatch(name::startsWith);
        }
        return TREE_FACTORY_METHODS.contains(name) && TREE_CODECS.stream().anyMatch(owner::isAssignableTo);
    }

    private static List<JavaClass> treeTypeIn(JavaType type) {
        return type.getAllInvolvedRawTypes().stream()
                .filter(JacksonTreeUsageCondition::isTreeType)
                .toList();
    }

    private static List<JavaClass> treeTypeArgumentsIn(JavaType supertype) {
        return treeTypeIn(supertype).stream()
                .filter(type -> !type.equals(supertype.toErasure()))
                .toList();
    }

    private static String sourceName(JavaClass type) {
        return type.tryGetComponentType()
                .map(component -> sourceName(component) + "[]")
                .orElseGet(type::getName);
    }

    private static boolean isTreeType(JavaClass type) {
        return type.isAssignableTo(JSON_NODE) || type.isAssignableTo(NODE_FACTORY);
    }

    private static boolean isCompilerGenerated(JavaCodeUnit codeUnit) {
        Set<JavaModifier> modifiers = codeUnit.getModifiers();
        return modifiers.contains(JavaModifier.SYNTHETIC) || modifiers.contains(JavaModifier.BRIDGE);
    }

    private static void violated(ConditionEvents events, Object item, String format, Object... args) {
        events.add(SimpleConditionEvent.violated(item, format.formatted(args)));
    }
}
