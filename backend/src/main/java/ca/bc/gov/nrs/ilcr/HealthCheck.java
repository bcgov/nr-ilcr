package ca.bc.gov.nrs.ilcr;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

public final class HealthCheck {
  private static final int HEALTHY = 0;
  private static final int UNHEALTHY = 1;

  private HealthCheck() {}

  public static void main(String[] args) {
    try {
      int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
      URI uri = URI.create("http://127.0.0.1:" + port + "/api/health/readiness");
      HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
      connection.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
      connection.setRequestMethod("GET");

      int status = connection.getResponseCode();
      System.exit(status >= 200 && status < 400 ? HEALTHY : UNHEALTHY);
    } catch (RuntimeException | java.io.IOException ex) {
      System.exit(UNHEALTHY);
    }
  }
}
