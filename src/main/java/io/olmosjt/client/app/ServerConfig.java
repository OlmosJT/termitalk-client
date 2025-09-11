package io.olmosjt.client.app;

import java.time.Duration;

/**
 * Immutable server configuration.
 * <p>
 * Holds basic parameters like port, host, timeouts, etc.
 */
public record ServerConfig(
        String host,
        int port,
        Duration clientTimeout
) {

  /**
   * Creates and returns a default {@link ServerConfig} instance.
   *
   * @return A {@link ServerConfig} configured with a host of "127.0.0.1", port 9000, and a timeout of 5 minutes.
   */
  public static ServerConfig defaultConfig() {
    return new ServerConfig("127.0.0.1", 9000, Duration.ofMinutes(5));
  }

}
