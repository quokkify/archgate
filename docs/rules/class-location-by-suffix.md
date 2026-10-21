# Class location by suffix

English · [Русский](class-location-by-suffix.ru.md)

`dev.quokkify.archgate.rules.archunit.ClassLocationBySuffixRule` · **ERROR** · main

A top-level class (not an enum, interface or annotation) must live in the package that matches its name suffix.

| Suffix | Package |
|---|---|
| `*Steps` | `..steps..` |
| `*Verification`, `*Verifications` | `..verifications..` |
| `*Generator` | `..generators..` |

**Why.** When a class name tells you its package, you find classes by naming alone.

```java
// ✗ violation
package com.acme.tests.api;
public class PaymentSteps { … }

// ✓ fix
package com.acme.tests.steps;
public class PaymentSteps { … }
```

**Register**

```
dev.quokkify.archgate.rules.archunit.ClassLocationBySuffixRule
```

**Configure.** You can replace the mapping. Values are ArchUnit package identifiers:

```java
public class AcmeClassLocationRule extends ClassLocationBySuffixRule {
  public AcmeClassLocationRule() {
    super(Map.of("Page", "..pages..", "Steps", "..steps.."));
  }
}
```

If no class has any of the suffixes, the rule reports *unable to verify*, which always fails the build.

[← All rules](../../README.md#built-in-rules)
