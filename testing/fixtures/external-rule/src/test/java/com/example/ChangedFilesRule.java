package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;
public class ChangedFilesRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Changed files";
  }

  @Override
  public Severity severity() {
    return Severity.INFO;
  }

  @Override
  public Class<ArchUnitContext> contextType() {
    return ArchUnitContext.class;
  }

  @Override
  public void verify(ArchUnitContext context) {
    context.environment().optional("archgate.changedFiles").ifPresent(changed ->
        checkViolations("Reports the changed files this run saw.", List.of("changed files: " + changed)));
  }
}
