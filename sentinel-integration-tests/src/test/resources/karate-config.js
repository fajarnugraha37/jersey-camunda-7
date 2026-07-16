function fn() {
  var config = {
    env: karate.env || 'local',
    baseUrl: karate.properties['sentinel.baseUrl'] || 'http://localhost:8080',
    keycloakBaseUrl: karate.properties['sentinel.keycloakBaseUrl'] || 'http://localhost:8081',
    realm: karate.properties['sentinel.realm'] || 'sentinel',
    clientId: karate.properties['sentinel.clientId'] || 'sentinel-api',
    defaultPassword: karate.properties['sentinel.defaultPassword'] || 'sentinel'
  };

  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 10000);
  karate.configure('logging', { pretty: true });

  return config;
}
