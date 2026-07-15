package com.sentinel.enforcement.application.messaging;

import java.util.function.Supplier;

public interface ApplicationTransactionManager {

  <T> T required(Supplier<T> work);

  default void required(Runnable work) {
    required(
        () -> {
          work.run();
          return null;
        });
  }
}
