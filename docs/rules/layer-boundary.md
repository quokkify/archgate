# Layer boundary

`dev.quokkify.archgate.rules.archunit.api.LayerBoundaryRule` · **ERROR** · main + test · artifact `archgate-rules-archunit-api` · abstract, not registrable itself

A base for keeping one package layer free of dependencies on the layers above it. A subclass names the lower layer and the packages it must not depend on, both relative to `rootPackage`. Every class under `<rootPackage>.<layer>` that depends on a class under one of the forbidden packages is reported: a call, a field or parameter type, a supertype, an annotation and so on.

**Why.** Layers are built on top of each other: an upper layer may use the one below it, never the reverse. A lower layer that reaches up into a layer built on it ties both together, so neither can change or be tested alone, and the dependency usually closes a cycle. Packages in the same module share one classpath, so the build tool cannot stop this; the rule does.

```java
public class DomainLayerBoundaryRule extends LayerBoundaryRule {
  public DomainLayerBoundaryRule() {
    super("domain", Set.of("persistence", "web"));
  }
}
```

With `rootPackage` set to `com.acme.shop`:

```java
// ✗ violation: com.acme.shop.domain.OrderPricing
public String price() {
  return new OrderController().show(); // com.acme.shop.web
}

// ✓ fix: the controller calls the domain, never the other way round
```

```
Method <com.acme.shop.domain.OrderPricing.price()> calls constructor <com.acme.shop.web.OrderController.<init>()> in (OrderPricing.java:7)
```

The rule is named after its arguments, for example `Layer boundary (domain must not depend on persistence, web)`, and the contract line in the report names the full packages.

**Empty scope.** Main and test are checked separately. An empty scope is skipped and named on the report line, for example `Layer boundary (domain must not depend on persistence, web) (TEST not checked: empty)`; if neither scope has a class under `rootPackage`, the rule fails as unable to verify. It also fails as unable to verify when no checked class lives in the layer package, because a misspelt or moved package would otherwise pass without checking anything. To fail on any empty scope, override `requireAllScopes()` to return `true` (see [Partial checks](../configuration.md#partial-checks)).

**Register** your subclass, not the base:

```
com.acme.shop.arch.DomainLayerBoundaryRule
```

**Configure.** The constructor takes the layer package and a non-empty set of forbidden packages, both relative to `rootPackage`. It throws `IllegalArgumentException` for a blank name, a name that starts or ends with a dot, and a forbidden package that equals the layer package, lies inside it or contains it. To guard several layers, write one subclass per layer.

[← All rules](../../README.md#built-in-rules)
