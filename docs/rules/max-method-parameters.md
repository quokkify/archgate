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
