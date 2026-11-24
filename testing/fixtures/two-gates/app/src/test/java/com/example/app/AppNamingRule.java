package com.example.app;

import java.util.List;

import com.example.helpers.NamedRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;

public class AppNamingRule extends NamedRule implements ArchitectureRule<ArchUnitContext> {

  public AppNamingRule() {
    super("App naming");
  }

  @Override
  public Severity severity() {
    return Severity.ERROR;
  }

  @Override
  public Class<ArchUnitContext> contextType() {
    return ArchUnitContext.class;
  }

  @Override
  public void verify(ArchUnitContext context) {
    List<String> violations = context.main().stream()
        .filter(type -> type.getSimpleName().contains("Legacy"))
        .map(type -> type.getName())
        .toList();
    checkViolations("Classes must not be named Legacy*", violations);
  }
}
