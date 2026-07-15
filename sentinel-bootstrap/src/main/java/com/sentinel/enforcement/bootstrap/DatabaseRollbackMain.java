package com.sentinel.enforcement.bootstrap;

public final class DatabaseRollbackMain {
  private DatabaseRollbackMain() {}

  public static void main(String[] args) {
    int rollbackCount = args.length == 0 ? 1 : Integer.parseInt(args[0]);
    ApplicationRuntime.rollback(AppConfiguration.fromEnvironment(), rollbackCount);
  }
}
