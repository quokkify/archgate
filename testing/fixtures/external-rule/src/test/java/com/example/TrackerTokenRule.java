package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
public class TrackerTokenRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Tracker token";
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
    context.environment().require("TRACKER_TOKEN");
  }
}
