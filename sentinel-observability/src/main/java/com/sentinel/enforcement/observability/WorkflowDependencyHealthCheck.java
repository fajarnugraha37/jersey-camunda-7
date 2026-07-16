package com.sentinel.enforcement.observability;

import java.util.function.BooleanSupplier;

public final class WorkflowDependencyHealthCheck implements DependencyHealthCheck {
  private final BooleanSupplier readySupplier;

  public WorkflowDependencyHealthCheck(BooleanSupplier readySupplier) {
    this.readySupplier = readySupplier;
  }

  @Override
  public DependencyHealth check() {
    return new DependencyHealth("workflow", readySupplier.getAsBoolean() ? "UP" : "DOWN");
  }
}
