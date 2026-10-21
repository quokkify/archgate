# Максимум параметров метода

[English](max-method-parameters.md) · Русский

`dev.quokkify.archgate.rules.archunit.MaxMethodParametersRule` · **WARNING** · main + test

Методы и конструкторы не должны объявлять больше 5 параметров (по умолчанию). Сгруппируй связанные параметры в тип или передавай builder.

**Зачем.** Длинный список параметров прячет недостающую абстракцию: вызывающие путают аргументы одного типа, и каждая новая настройка расширяет одну и ту же сигнатуру по всей кодовой базе.

```java
// ✗ нарушение
public void placeOrder(String user, String item, int count,
    String currency, boolean gift, String coupon) {
}

// ✓ исправлено
public record OrderRequest(String user, String item, int count,
    String currency, boolean gift, String coupon) { }

public void placeOrder(OrderRequest request) {
}
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.MaxMethodParametersRule
```

**Настройка.** Лимит настраивается — отнаследуйся от правила и передай максимум:

```java
public class MaxThreeParametersRule extends MaxMethodParametersRule {
  protected MaxThreeParametersRule() {
    super(3);
  }
}
```

[← Все правила](../../README.ru.md#встроенные-правила)
