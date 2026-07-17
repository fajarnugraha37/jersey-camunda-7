package com.sentinel.enforcement.integration.karate;

import io.karatelabs.junit6.Karate;
import org.junit.jupiter.api.DynamicNode;

class KarateRegressionIT {
  @Karate.Test
  Iterable<DynamicNode> regression() {
    return Karate.run("classpath:karate").tags("@regression").relativeTo(getClass());
  }
}
