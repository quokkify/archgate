# Проверки в тестах

[English](test-assertions.md) · Русский

`dev.quokkify.archgate.rules.archunit.TestAssertionsRule` · **WARNING** · test

Каждый тестовый метод должен что-то проверять через один из assertion-API. Тест без проверок выполняет код, но ничего не проверяет.

**Зачем.** Тест, который только вызывает продакшн-код, зелёный, даже если поведение сломано. Он раздувает цифры покрытия, ничего не охраняя.

```java
// ✗ нарушение
@Test
public void loginWorks() {
  page.login(USER); // выполняется, никогда не проверяет
}

// ✓ исправлено
@Test
public void loginWorks() {
  page.login(USER);
  assertEquals("Welcome", page.header());
}
```

Правило распознаёт вызовы, принадлежащие:

- `org.testng.Assert`, `org.testng.asserts.Assertion`, `org.testng.asserts.SoftAssert`,
- `org.assertj.core.api.Assertions`,
- `org.junit.jupiter.api.Assertions`.

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.TestAssertionsRule
```

**Настройка.** Настраиваются и аннотации, выбирающие тестовые методы, и классы assertion-API — отнаследуйся от правила и передай полные имена:

```java
public class MyTestAssertionsRule extends TestAssertionsRule {
  public MyTestAssertionsRule() {
    super(Set.of("my.framework.Test"), Set.of("my.framework.Assertions"));
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
