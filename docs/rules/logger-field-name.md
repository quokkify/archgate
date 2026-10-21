# Logger field name

English · [Русский](logger-field-name.ru.md)

`dev.quokkify.archgate.rules.archunit.LoggerFieldNameRule` · **ERROR** · main

A Log4j2 or SLF4J `Logger` field must be named `LOG` and be declared `static final`. Lombok `@Log4j2` and `@Slf4j` generate a field named `log`, so they break this rule.

**Why.** With one logger field name, log statements look the same and are easy to grep.

```java
// ✗ violation
private final Logger logger = LoggerFactory.getLogger(getClass());

// ✓ fix
private static final Logger LOG = LoggerFactory.getLogger(PaymentSteps.class);
```

**Register**

```
dev.quokkify.archgate.rules.archunit.LoggerFieldNameRule
```

**Configure.** You can use another field name:

```java
public class AcmeLoggerFieldNameRule extends LoggerFieldNameRule {
  public AcmeLoggerFieldNameRule() {
    super("LOGGER");
  }
}
```

[← All rules](../../README.md#built-in-rules)
