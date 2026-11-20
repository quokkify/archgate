package com.example;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.io.IOException;
public class TrackerServiceRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Tracker service";
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
    throw context.environment().unavailable("Tracker", new IOException("Connect timed out"));
  }
}
