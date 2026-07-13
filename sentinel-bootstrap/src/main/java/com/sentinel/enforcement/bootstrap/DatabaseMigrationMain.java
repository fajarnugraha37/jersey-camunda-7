package com.sentinel.enforcement.bootstrap;

public final class DatabaseMigrationMain {
  private DatabaseMigrationMain() {}

  public static void main(String[] args) {
    ApplicationRuntime.migrate(AppConfiguration.fromEnvironment());
  }
}
