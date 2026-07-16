package com.sentinel.enforcement.observability;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public final class SocketDependencyHealthCheck implements DependencyHealthCheck {
  private final String name;
  private final String host;
  private final int port;
  private final Duration timeout;

  public SocketDependencyHealthCheck(String name, String host, int port, Duration timeout) {
    this.name = name;
    this.host = host;
    this.port = port;
    this.timeout = timeout;
  }

  @Override
  public DependencyHealth check() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
      return new DependencyHealth(name, "UP");
    } catch (IOException exception) {
      return new DependencyHealth(name, "DOWN");
    }
  }
}
