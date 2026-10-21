# Имя поля логгера

[English](logger-field-name.md) · Русский

`dev.quokkify.archgate.rules.archunit.LoggerFieldNameRule` · **ERROR** · main

Поле `Logger` из Log4j2 или SLF4J должно называться `LOG` и быть объявлено как `static final`. Lombok-аннотации `@Log4j2` и `@Slf4j` генерируют поле с именем `log`, поэтому они нарушают это правило.

**Зачем.** Когда у всех логгер называется одинаково, строки логирования выглядят одинаково и их легко искать grep-ом.

```java
// ✗ violation
private final Logger logger = LoggerFactory.getLogger(getClass());

// ✓ fix
private static final Logger LOG = LoggerFactory.getLogger(PaymentSteps.class);
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.LoggerFieldNameRule
```

**Настройка.** Можно использовать другое имя поля:

```java
public class AcmeLoggerFieldNameRule extends LoggerFieldNameRule {
  public AcmeLoggerFieldNameRule() {
    super("LOGGER");
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
