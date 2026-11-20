# No Jackson tree API

`dev.quokkify.archgate.rules.archunit.api.JsonTreeUsageRule` · **WARNING** · test · artifact `archgate-rules-archunit-api`

Use typed models instead of the Jackson tree API (`JsonNode`, `readTree`, `valueToTree`, …), so the shape of each request and response stays checked by the compiler. The rule reports a class that:

- has a field, parameter or return type that is, or has a type argument that is, `JsonNode`, one of its subtypes such as `ObjectNode`, or `JsonNodeFactory`;
- extends a node type, or extends a generic type with a node type as argument, such as `new TypeReference<ObjectNode>() {}`;
- names a node type as a class literal, such as `mapper.readValue(json, JsonNode.class)`;
- builds nodes: `JsonNodeFactory`, a node constructor, `valueOf`, `getInstance`, or a node constant such as `BooleanNode.TRUE`;
- mutates a node: methods starting with `put`, `set`, `add`, `insert`, `remove`, `replace`, `retain` or `with`;
- calls `readTree`, `valueToTree`, `createObjectNode`, `createArrayNode` or `readValueAsTree` on an `ObjectMapper`, `JsonMapper`, another `TreeCodec` or a `JsonParser`.

Reading a node in place is not reported: a local variable, `path`, `get`, `asText` or iterating over it is fine, so a project-level JSON wrapper that hands out nodes for reading can still be used. Lambda parameters and other compiler-generated signatures are ignored, and so are generated classes.

**Why.** A tree hides the shape of the JSON: a misspelt field or a changed type turns into a missing node at run time instead of a compile error, and every test rebuilds the same structure by hand.

```java
// ✗ violations
JsonNode body = mapper.readTree(response);
ObjectNode request = mapper.createObjectNode().put("item", "book");

// ✓ fix
record OrderRequest(String item) { }
record OrderResponse(String id, String status) { }

OrderResponse body = mapper.readValue(response, OrderResponse.class);
OrderRequest request = new OrderRequest("book");
```

```
Method <com.example.OrderTest.parse(java.lang.String)> calls method <com.fasterxml.jackson.databind.ObjectMapper.readTree(java.lang.String)> in (OrderTest.java:21)
```

Jackson types are matched by name, so the rule adds no Jackson dependency. Subtypes such as `ObjectNode` are recognised through the Jackson classes on the classpath of the checked project, which a project that uses the tree API has anyway.

**Empty scope.** The default rule reads the test classes only. If there is no test class under `rootPackage`, it fails the build as unable to verify. A subclass that checks several scopes skips an empty one and names it on the report line, for example `No Jackson tree API (MAIN not checked: empty)`, and fails only when all are empty (see [Partial checks](../configuration.md#partial-checks)).

**Register**

```
dev.quokkify.archgate.rules.archunit.api.JsonTreeUsageRule
```

**Configure.** Subclass the rule and pass the scopes, the base types and the allowed classes:

```java
public class VerificationJsonTreeRule extends JsonTreeUsageRule {
  public VerificationJsonTreeRule() {
    super(
        Set.of(Scope.MAIN),
        Set.of("com.acme.tests.verify.BaseVerification"),
        Map.of("com.acme.tests.json.JsonBody", "the one wrapper that builds schema-less JSON"));
  }
}
```

- `scopes` must not be empty.
- `restrictedSupertypes` are fully qualified names. When the set is empty, every class in the scopes is checked; otherwise only classes assignable to at least one of them, including the base types themselves. The contract line in the report then names them, and the rule name gets their simple names, sorted, so two registered variants stay apart: `No Jackson tree API in BaseVerification`.
- `allowedClassesWithReason` maps the fully qualified name of each class that may use the tree API to the reason. Keys are matched exactly, so a nested class is written `Outer$Inner`.

Register `com.acme.tests.arch.VerificationJsonTreeRule` instead of, or next to, the base rule.

[← All rules](../../README.md#built-in-rules)
