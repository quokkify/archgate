# Без generic-исключений

[English](no-generic-throws.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoGenericThrowsRule` · **WARNING** · main

Методы и конструкторы не должны объявлять generic-исключения `Exception`, `RuntimeException` или `Throwable`. Объяви конкретное checked- или unchecked-исключение, которое код реально бросает.

**Зачем.** `throws Exception` прячет настоящие сценарии отказа и вынуждает вызывающих писать обезьяньи `catch (Exception e)`, которые глотают посторонние сбои. Конкретный тип делает обработку ошибок точной и переживает рефакторинг.

```java
// ✗ нарушение
public void upload(File file) throws Exception {
}

// ✓ исправлено
public void upload(File file) throws IOException, InvalidFileException {
}
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoGenericThrowsRule
```

**Настройка.** У этого правила нет параметров. Чтобы изменить severity, отнаследуйся от него и зарегистрируй наследника:

```java
public class StrictNoGenericThrowsRule extends NoGenericThrowsRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
