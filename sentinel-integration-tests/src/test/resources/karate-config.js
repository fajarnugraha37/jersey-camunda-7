function fn() {
  var config = {
    env: karate.env || 'local',
    baseUrl: karate.properties['sentinel.baseUrl'] || 'http://localhost:8080',
    keycloakBaseUrl: karate.properties['sentinel.keycloakBaseUrl'] || 'http://localhost:8081',
    mailpitBaseUrl: karate.properties['sentinel.mailpitBaseUrl'] || 'http://localhost:8025',
    realm: karate.properties['sentinel.realm'] || 'sentinel',
    clientId: karate.properties['sentinel.clientId'] || 'sentinel-api',
    defaultPassword: karate.properties['sentinel.defaultPassword'] || 'sentinel',
    waitUntil: function(predicate, timeoutMs, intervalMs) {
      var deadline = java.lang.System.currentTimeMillis() + timeoutMs;
      var sleepMs = intervalMs || 500;
      while (java.lang.System.currentTimeMillis() < deadline) {
        if (predicate()) {
          return true;
        }
        java.lang.Thread.sleep(sleepMs);
      }
      return predicate();
    },
    messagesBySubjectFragment: function(messages, fragment) {
      return karate.filter(messages, function(message) {
        return message.Subject && message.Subject.indexOf(fragment) > -1;
      });
    }
  };

  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 30000);
  karate.configure('logging', { pretty: true });

  return config;
}
