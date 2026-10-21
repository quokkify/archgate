# Без Thread.sleep

[English](no-thread-sleep.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoThreadSleepRule` · **ERROR** · main + test

Код не должен вызывать `Thread.sleep` или `TimeUnit.sleep`. Вместо этого жди нужного условия.

**Зачем.** Фиксированная задержка либо слишком короткая, и тесты становятся flaky, либо слишком длинная, и тесты медленные.

```java
// ✗ violation
Thread.sleep(2000);
assertThat(page.status()).isEqualTo("DONE");

// ✓ fix
await().atMost(Duration.ofSeconds(10)).until(() -> page.status().equals("DONE"));
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoThreadSleepRule
```

**Настройка.** У этого правила нет параметров. Чтобы понизить severity, унаследуйся от него и зарегистрируй наследника:

```java
public class SoftNoThreadSleepRule extends NoThreadSleepRule {
  @Override
  public Severity severity() {
    return Severity.WARNING;
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
