package com.sentinel.enforcement.domain.recommendation;

public final class RecommendationConflictException extends RuntimeException {
  private final String code;

  public RecommendationConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
