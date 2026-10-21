# Расположение класса по суффиксу

[English](class-location-by-suffix.md) · Русский

`dev.quokkify.archgate.rules.archunit.ClassLocationBySuffixRule` · **ERROR** · main

Класс верхнего уровня (не enum, interface и не annotation) должен лежать в пакете, который соответствует суффиксу его имени.

| Суффикс | Пакет |
|---|---|
| `*Steps` | `..steps..` |
| `*Verification`, `*Verifications` | `..verifications..` |
| `*Generator` | `..generators..` |

**Зачем.** Когда имя класса подсказывает его пакет, классы находятся по одному названию.

```java
// ✗ violation
package com.acme.tests.api;
public class PaymentSteps { … }

// ✓ fix
package com.acme.tests.steps;
public class PaymentSteps { … }
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.ClassLocationBySuffixRule
```

**Настройка.** Соответствие можно заменить. Значения — идентификаторы пакетов ArchUnit:

```java
public class AcmeClassLocationRule extends ClassLocationBySuffixRule {
  public AcmeClassLocationRule() {
    super(Map.of("Page", "..pages..", "Steps", "..steps.."));
  }
}
```

Если ни у одного класса нет этих суффиксов, правило сообщает *unable to verify*, и это всегда роняет сборку.

[← Все правила](../../README.ru.md#встроенные-правила)
