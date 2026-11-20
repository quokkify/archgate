package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.ViolationGroup;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;

public class DeletedIssueRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Deleted issues";
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
    checkViolationGroups(
        "Cases refer to existing issues",
        List.of(
            new ViolationGroup("case is deleted", List.of(index.get(0), index.get(1))),
            new ViolationGroup("case is archived", List.of()),
            new ViolationGroup("case has no title", List.of(index.get(2)))));
  }
}
