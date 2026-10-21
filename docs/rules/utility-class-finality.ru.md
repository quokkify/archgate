# Финальность utility-классов

[English](utility-class-finality.md) · Русский

`dev.quokkify.archgate.rules.archunit.UtilityClassFinalityRule` · **WARNING** · main

Класс, у которого все методы static, должен быть `final` и иметь приватный конструктор.

**Зачем.** Utility-класс — это пространство имён для функций, а не тип для наследования или инстанцирования. Без `final` и приватного конструктора ничто не мешает создать подкласс или экземпляр, которые неверно используют stateless-хелперы.

```java
// ✗ нарушение
public class Strings {
  public static String trim(String value) {
    return value.strip();
  }
}

// ✓ исправлено
public final class Strings {
  private Strings() {
  }

  public static String trim(String value) {
    return value.strip();
  }
}
```
Классы хотя бы с одним instance-методом, классы с методом `main`, интерфейсы, enum и abstract-классы правило не трогает.

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.UtilityClassFinalityRule
```

**Настройка.** У этого правила нет параметров. Чтобы повысить severity, отнаследуйся от него и зарегистрируй наследника:

```java
public class StrictUtilityClassRule extends UtilityClassFinalityRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
