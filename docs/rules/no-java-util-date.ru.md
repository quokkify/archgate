# Без java.util.Date

[English](no-java-util-date.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoJavaUtilDateRule` · **WARNING** · main + test

Код не должен зависеть от `java.util.Date`. Вместо него используй типы из `java.time`: `Instant`, `LocalDateTime` или `ZonedDateTime`.

**Зачем.** `Date` изменяемый и не хранит часовой пояс.

```java
// ✗ violation
Date expiry = new Date(System.currentTimeMillis() + 3_600_000);

// ✓ fix
Instant expiry = Instant.now().plus(Duration.ofHours(1));
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoJavaUtilDateRule
```

**Настройка.** Некоторые API всё ещё требуют `Date`, например `Cookie` из Selenium. Разреши классы, которые связывают код с такими API. Ключ — полное имя класса, значение — причина:

```java
public class AcmeNoJavaUtilDateRule extends NoJavaUtilDateRule {
  public AcmeNoJavaUtilDateRule() {
    super(Map.of("com.acme.tests.ui.CookieFactory", "Selenium Cookie API takes java.util.Date"));
  }
}
```

Регистрируй `com.acme.tests.arch.AcmeNoJavaUtilDateRule` вместо базового правила.

[← Все правила](../../README.ru.md#встроенные-правила)
