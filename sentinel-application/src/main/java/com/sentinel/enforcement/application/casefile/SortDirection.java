package com.sentinel.enforcement.application.casefile;

public enum SortDirection {
  ASC,
  DESC;

  public boolean isAscending() {
    return this == ASC;
  }
}
