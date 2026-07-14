package com.sentinel.enforcement.workflow;

import org.camunda.bpm.engine.ProcessEngine;

final class SingleProcessEngineProvider implements ProcessEngineProvider {
  private final ProcessEngine processEngine;

  SingleProcessEngineProvider(ProcessEngine processEngine) {
    this.processEngine = processEngine;
  }

  @Override
  public ProcessEngine get() {
    return processEngine;
  }

  @Override
  public void close() {
    processEngine.close();
  }
}
