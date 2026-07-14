package com.sentinel.enforcement.workflow;

import org.camunda.bpm.engine.ProcessEngine;

public interface ProcessEngineProvider extends AutoCloseable {

  ProcessEngine get();

  @Override
  void close();
}
