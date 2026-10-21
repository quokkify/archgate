# hashCode и equals вместе

[English](hash-code-equals-together.md) · Русский

`dev.quokkify.archgate.rules.archunit.HashCodeEqualsTogetherRule` · **ERROR** · main

Класс, переопределяющий один из `equals` или `hashCode`, должен переопределить оба.

**Зачем.** Переопределение только одного ломает контракт hash-коллекций: равные объекты попадают в разные корзины, `HashSet.contains` возвращает неверный ответ, поиск в `HashMap` теряет записи.

```java
// ✗ нарушение
public class UserId {
  private final String id;

  @Override
  public boolean equals(Object other) { /* ... */ }
  // hashCode отсутствует
}

// ✓ исправлено
public class UserId {
  private final String id;

  @Override
  public boolean equals(Object other) { /* ... */ }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.HashCodeEqualsTogetherRule
```

**Настройка.** У этого правила нет параметров. Чтобы понизить severity, отнаследуйся от него и зарегистрируй наследника:

```java
public class SoftHashCodeEqualsRule extends HashCodeEqualsTogetherRule {
  @Override
  public Severity severity() {
    return Severity.WARNING;
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
