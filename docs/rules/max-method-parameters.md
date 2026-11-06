# Max method parameters

`dev.quokkify.archgate.rules.archunit.MaxMethodParametersRule` · **WARNING** · main + test

Methods and constructors must not declare more than 5 parameters (by default). Group the related parameters into a type, or pass a builder.

**Why.** A long parameter list hides a missing abstraction: callers mix up arguments of the same type, and every new setting widens the same signature across the codebase.

```java
// ✗ violation
public void placeOrder(String user, String item, int count,
    String currency, boolean gift, String coupon) {
}

// ✓ fix
public record OrderRequest(String user, String item, int count,
    String currency, boolean gift, String coupon) { }

public void placeOrder(OrderRequest request) {
}
```

**Empty scope.** Main and test are checked separately. A module without test classes is still checked in main, and one without main classes in test; the report line then names the scope that was not checked, for example `Max method parameters (TEST not checked: empty)`. If neither scope has a class under `rootPackage`, the rule fails the build as unable to verify. To fail on any empty scope instead, subclass the rule and override `requireAllScopes()` to return `true` (see [Partial checks](../configuration.md#partial-checks)).

**Register**

```
dev.quokkify.archgate.rules.archunit.MaxMethodParametersRule
```

**Configure.** The limit is configurable — subclass the rule and pass the maximum:

```java
public class MaxThreeParametersRule extends MaxMethodParametersRule {
  public MaxThreeParametersRule() {
    super(3);
  }
}
```

[← All rules](../../README.md#built-in-rules)
