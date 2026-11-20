package dev.quokkify.archgate.rules.archunit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class JsonTreeUsageRuleTest {

    private static final String USAGE = "archfixtures.jsontree.usage";
    private static final String VERIFICATION = "archfixtures.jsontree.verification";
    private static final String ALLOWED_USAGE = USAGE + ".AllowedJacksonUsage";
    private static final String ALLOWED_VERIFICATION = VERIFICATION + ".AllowedJsonTreeVerification";
    private static final String BASE_VERIFICATION = VERIFICATION + ".BaseVerification";
    private static final String PROJECT_BASE = VERIFICATION + ".ProjectBaseVerification";
    private static final JavaClasses USAGE_CLASSES = new ClassFileImporter().importPackages(USAGE);
    private static final JavaClasses VERIFICATION_CLASSES = new ClassFileImporter().importPackages(VERIFICATION);

    private static List<String> usageViolations() {
        return usageViolations(Map.of());
    }

    private static List<String> usageViolations(Map<String, String> allowed) {
        return ArchRuleChecks.violations(JsonTreeUsageRule.definition(Set.of(), allowed), USAGE_CLASSES);
    }

    private static List<String> verificationViolations(Set<String> supertypes, Map<String, String> allowed) {
        return ArchRuleChecks.violations(JsonTreeUsageRule.definition(supertypes, allowed), VERIFICATION_CLASSES);
    }

    private static final class MainVerificationRule extends JsonTreeUsageRule {

        MainVerificationRule(Set<String> supertypes, Map<String, String> allowed) {
            super(Set.of(Scope.MAIN), supertypes, allowed);
        }
    }

    // --- every class in the scopes (empty restricted supertypes) ---

    @Test(description = "Reading and building JSON through a wrapper and reading nodes in place passes")
    public void wrapperUsagePasses() {
        assertThat(usageViolations())
                .isNotEmpty()
                .noneSatisfy(detail -> assertThat(detail).contains("ValidWrapperUsage"));
    }

    @Test(description = "Value nodes built through static factories and constants are reported")
    public void valueNodeFactoriesAreReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("ValueNodeFactories.text", "TextNode.valueOf"))
                .anySatisfy(detail -> assertThat(detail).contains("ValueNodeFactories.constants", "BooleanNode.TRUE"))
                .anySatisfy(
                        detail -> assertThat(detail).contains("ValueNodeFactories.constants", "NullNode.getInstance"));
    }

    @Test(description = "A Jackson tree type as TypeReference argument is reported")
    public void typeReferenceArgumentIsReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("TypeReferenceTree$1", "supertype", "ObjectNode"));
    }

    @Test(description = "Tree building through JsonMapper and mutation by method reference are reported")
    public void jsonMapperAndMethodReferenceAreReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("JsonMapperAndMethodReference.parse", "readTree"))
                .anySatisfy(
                        detail -> assertThat(detail).contains("JsonMapperAndMethodReference.putter", "ObjectNode.put"));
    }

    @Test(description = "Jackson tree types in fields are reported, including generic arguments")
    public void jacksonTreeFieldIsReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("JsonNodeField.payload", "ObjectNode"))
                .anySatisfy(detail -> assertThat(detail).contains("GenericJsonNodeField.items", "JsonNode"));
    }

    @Test(description = "Jackson tree types in parameters and return types are reported")
    public void jacksonTreeSignatureIsReported() {
        assertThat(usageViolations())
                .anySatisfy(
                        detail -> assertThat(detail).contains("JsonNodeSignatures.describe", "parameter", "JsonNode"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonNodeSignatures.empty", "returns", "ArrayNode"));
    }

    @Test(description = "Building a JSON tree through ObjectMapper is reported")
    public void objectMapperTreeIsReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("ObjectMapperTree.parse", "readTree"))
                .anySatisfy(detail -> assertThat(detail).contains("ObjectMapperTree.convert", "valueToTree"));
    }

    @Test(description = "Building a JSON tree through JsonNodeFactory or node constructors is reported")
    public void nodeFactoryAndConstructorsAreReported() {
        assertThat(usageViolations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("JsonNodeFactoryUsage.build", "JsonNodeFactory.objectNode"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonNodeFactoryUsage.build", "TextNode.<init>"));
    }

    @Test(description = "Converting to a Jackson tree type by class literal is reported")
    public void classLiteralIsReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("JsonNodeClassLiteral.parse", "ObjectNode"));
    }

    @Test(description = "Mutating the tree a wrapper hands out is reported")
    public void wrapperTreeMutationIsReported() {
        assertThat(usageViolations())
                .anySatisfy(
                        detail -> assertThat(detail).contains("WrapperTreeMutation.putThroughJson", "ObjectNode.put"))
                .anySatisfy(detail -> assertThat(detail).contains("WrapperTreeMutation", "ObjectNode.withArray"))
                .anySatisfy(detail -> assertThat(detail).contains("WrapperTreeMutation", "ArrayNode.add"));
    }

    @Test(description = "Regression guard: a Jackson tree type as argument of a generic interface is reported")
    public void interfaceTypeArgumentIsReported() {
        assertThat(usageViolations())
                .anySatisfy(detail -> assertThat(detail).contains("NodeSupplier", "supertype", "JsonNode"))
                .noneSatisfy(detail -> assertThat(detail).contains("TextSupplier"));
    }

    @Test(description = "An own subclass of a Jackson tree type is reported")
    public void treeSubclassIsReported() {
        assertThat(usageViolations()).anySatisfy(detail -> assertThat(detail).contains("CustomJsonNode", "extends"));
    }

    @Test(description = "Lambda parameters of Jackson tree types are not reported as signatures")
    public void syntheticLambdaSignatureIsNotReported() {
        assertThat(usageViolations()).noneSatisfy(detail -> assertThat(detail).contains("lambda$", "parameter"));
    }

    @Test(description = "An allowlisted class is not reported while the same usage elsewhere is")
    public void allowlistedClassIsNotReported() {
        assertThat(usageViolations()).anySatisfy(detail -> assertThat(detail).contains("AllowedJacksonUsage.describe"));
        assertThat(usageViolations(Map.of(ALLOWED_USAGE, "fixture boundary")))
                .noneSatisfy(detail -> assertThat(detail).contains("AllowedJacksonUsage"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonNodeSignatures.describe"));
    }

    @Test(description = "Without restricted supertypes every class in the scope is checked")
    public void emptySupertypesCheckEveryClass() {
        assertThat(verificationViolations(Set.of(), Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("JsonTreeHelper.describe"))
                .anySatisfy(detail -> assertThat(detail).contains("ProjectJsonTreeVerification.describe"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonTreeVerification.findItem"));
    }

    @Test(description = "The default rule verifies test classes with the typed-model contract")
    public void defaultRuleVerifiesTestClasses() {
        assertThatThrownBy(() -> new JsonTreeUsageRule().verify(FixtureContexts.of(USAGE)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("Use typed models instead of the Jackson tree API")
                .hasMessageContaining("JsonNodeField.payload")
                .hasMessageContaining("AllowedJacksonUsage");
    }

    @Test(description = "The configured allowlist applies in verify")
    public void allowlistAppliesInVerify() {
        JsonTreeUsageRule rule =
                new JsonTreeUsageRule(Set.of(Scope.TEST), Set.of(), Map.of(ALLOWED_USAGE, "reason")) {};

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of(USAGE)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("JsonNodeField.payload")
                .hasMessageNotContaining("AllowedJacksonUsage");
    }

    @Test(description = "The default rule fails closed when there are no test classes")
    public void defaultRuleWithoutTestClassesIsUnableToVerify() {
        ArchUnitContext mainOnly = FixtureContexts.of(USAGE, List.of(FixtureContexts.classesDir()), List.of());

        assertThatThrownBy(() -> new JsonTreeUsageRule().verify(mainOnly)).isInstanceOf(UnableToVerifyError.class);
    }

    @Test(description = "The default rule inspects only test classes with warning severity")
    public void defaultRuleInspectsTestClassesOnly() {
        assertThat(new JsonTreeUsageRule().scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(new JsonTreeUsageRule().severity()).isEqualTo(Severity.WARNING);
        assertThat(new JsonTreeUsageRule().contextType()).isEqualTo(ArchUnitContext.class);
    }

    // --- only subtypes of restricted supertypes ---

    @Test(description = "Tree parsing, tree return types and node constants in a restricted class are reported")
    public void jsonTreeInRestrictedClassIsReported() {
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION), Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("JsonTreeVerification.findItem", "JsonNode.class"))
                .anySatisfy(
                        detail -> assertThat(detail).contains("JsonTreeVerification.findItem", "returns", "JsonNode"))
                .anySatisfy(detail ->
                        assertThat(detail).contains("JsonTreeVerification.verifyItemIsInvalid", "BooleanNode.FALSE"));
    }

    @Test(description = "Typed models and wrapper reads in a restricted class pass")
    public void typedAndWrapperReadsPass() {
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION), Map.of()))
                .isNotEmpty()
                .noneSatisfy(detail -> assertThat(detail).contains("TypedVerification"));
    }

    @Test(description = "Classes not assignable to a restricted supertype are not checked")
    public void unrestrictedClassIsNotChecked() {
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION), Map.of()))
                .noneSatisfy(
                        detail -> assertThat(detail).containsAnyOf("JsonTreeHelper", "ProjectJsonTreeVerification"));
    }

    @Test(description = "Each restricted supertype selects its own subclasses")
    public void everyRestrictedSupertypeIsChecked() {
        assertThat(verificationViolations(Set.of(PROJECT_BASE), Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("ProjectJsonTreeVerification.describe", "JsonNode"))
                .noneSatisfy(detail -> assertThat(detail).contains("JsonTreeVerification.findItem"));
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION, PROJECT_BASE), Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("ProjectJsonTreeVerification.describe"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonTreeVerification.findItem"));
    }

    @Test(description = "An allowlisted restricted class is not reported while the same usage elsewhere is")
    public void allowlistedRestrictedClassIsNotReported() {
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION), Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("AllowedJsonTreeVerification.describe"));
        assertThat(verificationViolations(Set.of(BASE_VERIFICATION), Map.of(ALLOWED_VERIFICATION, "boundary")))
                .noneSatisfy(detail -> assertThat(detail).contains("AllowedJsonTreeVerification"))
                .anySatisfy(detail -> assertThat(detail).contains("JsonTreeVerification.findItem"));
    }

    @Test(description = "A rule over main classes reads the main view with its supertypes and allowlist")
    public void mainScopeRuleVerifiesMainClasses() {
        JsonTreeUsageRule rule = new MainVerificationRule(
                Set.of(BASE_VERIFICATION, PROJECT_BASE), Map.of(ALLOWED_VERIFICATION, "reason"));
        ArchUnitContext mainOnly = FixtureContexts.of(VERIFICATION, List.of(FixtureContexts.classesDir()), List.of());

        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.MAIN));
        assertThatThrownBy(() -> rule.verify(mainOnly))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("Use typed models instead of the Jackson tree API")
                .hasMessageContaining(BASE_VERIFICATION)
                .hasMessageContaining("ProjectJsonTreeVerification.describe")
                .hasMessageContaining("JsonTreeVerification.findItem")
                .hasMessageNotContaining("AllowedJsonTreeVerification")
                .hasMessageNotContaining("JsonTreeHelper");
    }

    @Test(description = "A rule over main classes fails closed when there are no main classes")
    public void mainScopeRuleWithoutMainClassesIsUnableToVerify() {
        JsonTreeUsageRule rule = new MainVerificationRule(Set.of(BASE_VERIFICATION), Map.of());
        ArchUnitContext testOnly = FixtureContexts.of(VERIFICATION, List.of(), List.of(FixtureContexts.classesDir()));

        assertThatThrownBy(() -> rule.verify(testOnly)).isInstanceOf(UnableToVerifyError.class);
    }

    @Test(description = "A restricted rule passes when no class in the scope matches the supertypes")
    public void restrictedRuleWithoutMatchingClassesPasses() {
        JsonTreeUsageRule rule = new MainVerificationRule(Set.of("org.example.Missing"), Map.of());

        assertThatCode(() -> rule.verify(FixtureContexts.of(VERIFICATION))).doesNotThrowAnyException();
    }

    @Test(description = "An empty set of scopes is rejected")
    public void emptyScopesAreRejected() {
        assertThatThrownBy(() -> new JsonTreeUsageRule(Set.of(), Set.of(), Map.of()) {})
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test(description = "The name lists the simple names of the restricted supertypes, sorted")
    public void nameListsRestrictedSupertypes() {
        assertThat(new JsonTreeUsageRule().name()).isEqualTo("No Jackson tree API");
        assertThat(new MainVerificationRule(Set.of(PROJECT_BASE, BASE_VERIFICATION), Map.of()).name())
                .isEqualTo("No Jackson tree API in BaseVerification, ProjectBaseVerification");
    }
}
