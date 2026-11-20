package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;

public class IssueIndexReaderRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Issue index reader";
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
    List<String> index = context.shared("issue index", () -> List.of("ISSUE-1", "ISSUE-2", "ISSUE-3"));
    if (index.isEmpty()) {
      throw new IllegalStateException("The issue index is empty");
    }
  }
}
