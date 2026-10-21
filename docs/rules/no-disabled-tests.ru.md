# Без отключённых тестов

[English](no-disabled-tests.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoDisabledTestsRule` · **ERROR** · test

Тесты нельзя отключать: ни `@Ignore` (TestNG) / `@Disabled` (JUnit 5) на классе или методе, ни `@Test(enabled = false)` (TestNG). Отключённый тест ничего не проверяет, хотя всё ещё выглядит как покрытие — удали его, если он устарел, или почини и включи обратно.

**Зачем.** Отключённый тест невидим в отчётах, но продолжает притворяться, что проверяет поведение. Цифры покрытия остаются зелёными, а проверяемый путь может молча сломаться.

```java
// ✗ нарушение
@Ignore("flaky")
public class LoginTest {

  @Test(enabled = false)
  public void loginRejectsWrongPassword() {
  }
}

// ✓ исправлено
public class LoginTest {

  @Test
  public void loginRejectsWrongPassword() {
  }
}
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoDisabledTestsRule
```

**Настройка.** Правило из коробки понимает TestNG (`org.testng.annotations.Test`, `org.testng.annotations.Ignore`) и JUnit 5 (`org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Disabled`). Чтобы проверять другие фреймворки, отнаследуйся от правила и передай полные имена аннотаций:

```java
public class MyNoDisabledTestsRule extends NoDisabledTestsRule {
  protected MyNoDisabledTestsRule() {
    super(Set.of("my.framework.Test"), Set.of("my.framework.Disable"));
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
