package com.sentinel.enforcement.application.messaging;

public record TransactionOptions(TransactionIsolation isolation, boolean readOnly, String label) {

  public TransactionOptions {
    if (isolation == null) {
      throw new IllegalArgumentException("isolation is required.");
    }
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label is required.");
    }
  }

  public static TransactionOptions defaultWrite() {
    return new TransactionOptions(TransactionIsolation.READ_COMMITTED, false, "default-write");
  }

  public static TransactionOptions write(TransactionIsolation isolation, String label) {
    return new TransactionOptions(isolation, false, label);
  }

  public static TransactionOptions readOnly(TransactionIsolation isolation, String label) {
    return new TransactionOptions(isolation, true, label);
  }
}
