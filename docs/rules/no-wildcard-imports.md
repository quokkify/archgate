# No wildcard imports

English · [Русский](no-wildcard-imports.ru.md)

`dev.quokkify.archgate.rules.javaparser.NoWildcardImportsRule` · **WARNING** · main + test · backend: JavaParser (`archgate-rules-javaparser`)

An import must name the type. The rule reports every `import x.y.*;` declaration. Static wildcard imports (`import static x.y.Z.*;`) are allowed.

**Why.** A wildcard import hides where a name comes from, and a new class added to the imported package can silently change what a name resolves to. Imports exist only in the sources, so this rule reads source files instead of compiled classes.

A finding is reported as the path of the file relative to its source directory, the line and the import:

```
com/acme/Orders.java:3 import java.util.*
```

```java
// ✗ violation
import java.util.*;

// ✓ fix
import java.util.List;
import java.util.Map;

// ✓ allowed
import static org.assertj.core.api.Assertions.*;
```

**Register**

```
dev.quokkify.archgate.rules.javaparser.NoWildcardImportsRule
```

Add `dev.quokkify.archgate:archgate-rules-javaparser` as a test dependency. It brings `archgate-context-javaparser`, which reads the source directories of the checked modules.

**Configure.** This rule has no parameters. To change its severity, subclass it and register the subclass:

```java
public class StrictNoWildcardImportsRule extends NoWildcardImportsRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

If no source file is found for a scope the rule checks, it reports *unable to verify*, which always fails the build.

[← All rules](../../README.md#built-in-rules)
