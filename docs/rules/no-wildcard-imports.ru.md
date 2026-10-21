# Без импортов с `*`

[English](no-wildcard-imports.md) · Русский

`dev.quokkify.archgate.rules.javaparser.NoWildcardImportsRule` · **WARNING** · main + test · backend: JavaParser (`archgate-rules-javaparser`)

Импорт должен называть тип. Правило сообщает о каждом объявлении `import x.y.*;`. Статические импорты с `*` (`import static x.y.Z.*;`) разрешены.

**Зачем.** Импорт с `*` скрывает, откуда взялось имя, а новый класс в импортируемом пакете может незаметно изменить то, во что разрешается имя. Импорты есть только в исходниках, поэтому правило читает исходные файлы, а не скомпилированные классы.

Находка выводится как путь файла относительно его каталога исходников, номер строки и импорт:

```
com/acme/Orders.java:3 import java.util.*
```

```java
// ✗ нарушение
import java.util.*;

// ✓ исправление
import java.util.List;
import java.util.Map;

// ✓ разрешено
import static org.assertj.core.api.Assertions.*;
```

**Регистрация**

```
dev.quokkify.archgate.rules.javaparser.NoWildcardImportsRule
```

Добавь `dev.quokkify.archgate:archgate-rules-javaparser` как test-зависимость. Он приносит `archgate-context-javaparser`, который читает каталоги исходников проверяемых модулей.

**Настройка.** У этого правила нет параметров. Чтобы поменять severity, унаследуйся от него и зарегистрируй наследника:

```java
public class StrictNoWildcardImportsRule extends NoWildcardImportsRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

Если для проверяемого среза не найдено ни одного исходного файла, правило сообщает *unable to verify*, и это всегда роняет сборку.

[← Все правила](../../README.ru.md#встроенные-правила)
