# Без использования deprecated API

[English](no-deprecated-api-usage.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoDeprecatedApiUsageRule` · **WARNING** · main + test

Код не должен использовать ничего, помеченного `@Deprecated` — поля, методы, конструкторы, суперклассы, интерфейсы и сами deprecated-классы. Следующий апгрейд зависимостей может их удалить; замени использование на поддерживаемую альтернативу из документации `@Deprecated`.

**Зачем.** Deprecated API работает сегодня и ломается при следующем апгрейде. Найти все использования сейчас, предупреждением на сборке, дешевле, чем получить сломанную сборку накануне релиза.

```java
// ✗ нарушение
public String token() {
  return LegacyClient.OLD_FIELD;   // deprecated-поле
}

// ✓ исправлено
public String token() {
  return CurrentClient.tokenField();
}
```

Правило сообщает о:

- доступе к deprecated-полю,
- вызовах deprecated-метода — включая методы, возвращающие deprecated-тип или принимающие deprecated-параметры,
- вызовах deprecated-конструктора,
- наследовании deprecated-класса или реализации deprecated-интерфейса.

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoDeprecatedApiUsageRule
```

**Настройка.** У этого правила нет параметров. Чтобы повысить severity, отнаследуйся от него и зарегистрируй наследника:

```java
public class StrictNoDeprecatedApiUsageRule extends NoDeprecatedApiUsageRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
