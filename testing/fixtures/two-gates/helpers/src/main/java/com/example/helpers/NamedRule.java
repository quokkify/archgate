package com.example.helpers;

public abstract class NamedRule {

  private final String name;

  protected NamedRule(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }
}
