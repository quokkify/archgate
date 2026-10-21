package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class CustomRule implements ArchitectureRule<CustomContext> {

  @Override
  public String name() {
    return "Custom rule";
  }

  @Override
  public Severity severity() {
    return Severity.ERROR;
  }

  @Override
  public Class<CustomContext> contextType() {
    return CustomContext.class;
  }

  @Override
  public void verify(CustomContext context) {}
}
