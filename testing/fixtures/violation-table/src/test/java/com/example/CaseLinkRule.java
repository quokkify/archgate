package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.ViolationGroup;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;

public class CaseLinkRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Case links";
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
    checkViolationGroups(
        "Every case id on a test method must identify an existing case.",
        List.of(
            new ViolationGroup("case is deleted", List.of("C7  LoginTest.java:12")),
            ViolationGroup.table(
                "case is in another stage",
                List.of("CASE", "ACTUAL", "LOCATION"),
                List.of(
                    List.of("C101", "staging", "CheckoutTest.java:40"),
                    List.of("C2", "production", "CartTest.java:7")))));
  }
}
