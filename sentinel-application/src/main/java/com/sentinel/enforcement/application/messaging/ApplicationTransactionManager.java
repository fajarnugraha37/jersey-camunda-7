package com.sentinel.enforcement.application.messaging;

import java.util.function.Supplier;

public interface ApplicationTransactionManager {

  <T> T required(Supplier<T> work);

  default <T> T required(TransactionOptions options, Supplier<T> work) {
    return required(work);
  }

  default void required(Runnable work) {
    required(
        () -> {
          work.run();
          return null;
        });
  }

  default void required(TransactionOptions options, Runnable work) {
    required(
        options,
        () -> {
          work.run();
          return null;
        });
  }
}
