package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.health.HealthStatus;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.workflow.WorkflowRuntime;
import java.time.Clock;
import java.time.Instant;

public final class WorkflowAwareHealthStatusService implements HealthStatusService {
  private final DatabaseHealthService databaseHealthService;
  private final WorkflowRuntime workflowRuntime;
  private final Clock clock;

  public WorkflowAwareHealthStatusService(
      DatabaseHealthService databaseHealthService, WorkflowRuntime workflowRuntime, Clock clock) {
    this.databaseHealthService = databaseHealthService;
    this.workflowRuntime = workflowRuntime;
    this.clock = clock;
  }

  @Override
  public HealthStatus currentStatus() {
    HealthStatus databaseStatus = databaseHealthService.currentStatus();
    boolean workflowReady = workflowRuntime.isReady();
    Instant timestamp = clock.instant();
    return new HealthStatus(
        databaseStatus.healthy() && workflowReady, databaseStatus.database(), timestamp);
  }
}
