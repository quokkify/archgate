# Без вывода в консоль

[English](no-console-output.md) · Русский

`dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule` · **ERROR** · main

Продакшен-код не должен писать в консоль. Правило запрещает:
- `System.out`
- `System.err`
- `Throwable.printStackTrace()`
- `Thread.dumpStack()`

**Зачем.** Вывод в консоль обходит конфигурацию логирования, поэтому у него нет уровней, формата и маршрутизации.

```java
// ✗ violation
System.out.println("Created user " + id);
e.printStackTrace();

// ✓ fix
private static final Logger LOG = LoggerFactory.getLogger(UserSteps.class);
LOG.info("Created user {}", id);
LOG.error("User creation failed", e);
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule
```

**Настройка.** У этого правила нет параметров. Чтобы поменять severity, унаследуйся от него и переопредели `severity()`.

[← Все правила](../../README.ru.md#встроенные-правила)
