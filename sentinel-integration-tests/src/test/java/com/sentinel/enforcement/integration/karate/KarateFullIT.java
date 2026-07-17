package com.sentinel.enforcement.integration.karate;

import io.karatelabs.junit6.Karate;
import org.junit.jupiter.api.DynamicNode;

class KarateFullIT {
  @Karate.Test
  Iterable<DynamicNode> full() {
    return Karate.run("classpath:karate/full/full-suite.feature").relativeTo(getClass());
  }
}
