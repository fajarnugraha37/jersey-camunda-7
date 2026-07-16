package com.sentinel.enforcement.integration.karate;

import io.karatelabs.junit6.Karate;
import org.junit.jupiter.api.DynamicNode;

class KarateSmokeIT {

  @Karate.Test
  Iterable<DynamicNode> smoke() {
    return Karate.run("classpath:karate/smoke").tags("@smoke").relativeTo(getClass());
  }
}
