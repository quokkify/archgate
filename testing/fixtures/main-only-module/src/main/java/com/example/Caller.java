package com.example;

public class Caller {

  @SuppressWarnings("deprecation")
  public String greet() {
    return Legacy.greet();
  }
}
