# Регистрация сервисов

[English](service-registration.md) · Русский

`dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule` · **ERROR** · main + test

Правило проверяет каждый файл провайдеров `META-INF/services/*` в проверяемых модулях, а не только файл ArchGate. Каждый перечисленный класс должен:
- существовать;
- реализовывать интерфейс сервиса, названный в имени файла;
- быть публичным и неабстрактным;
- иметь публичный конструктор без аргументов.

**Зачем.** `ServiceLoader` сообщает о сломанном файле провайдеров только в runtime, и часто только про первую плохую строку.

```
# ✗ violation — META-INF/services/org.testng.ITestNGListener
com.acme.tests.listeners.RetryListner        ← typo, class not found
com.acme.tests.listeners.AbstractListener    ← abstract

# ✓ fix
com.acme.tests.listeners.RetryListener
```

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule
```

**Настройка.** У этого правила нет параметров. Чтобы поменять severity, унаследуйся от него и переопредели `severity()`.

[← Все правила](../../README.ru.md#встроенные-правила)
