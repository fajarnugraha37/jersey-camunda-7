package com.sentinel.enforcement.bootstrap;

public final class SentinelMain {
  private SentinelMain() {}

  public static void main(String[] args) throws Exception {
    try (ApplicationRuntime ignored =
        ApplicationRuntime.start(AppConfiguration.fromEnvironment())) {
      Thread.currentThread().join();
    }
  }
}
