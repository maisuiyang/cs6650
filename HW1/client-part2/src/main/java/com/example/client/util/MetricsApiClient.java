package com.example.client.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Calls server {@code GET /api/metrics} after a load phase (Assignment 3: log on client, screenshot for report).
 */
public final class MetricsApiClient {

  private static final int PREVIEW_CHARS = 4_000;
  /** Widen query window so DB/analytics rows (async consumer) fall inside the slice. */
  private static final long WINDOW_PADDING_MS = 10 * 60_000L;

  private MetricsApiClient() {}

  public static void fetchAndSave(
      String host, int httpPort, String phase, long windowStartMs, long windowEndMs, String resultsDir) {
    long qStart = windowStartMs - WINDOW_PADDING_MS;
    long qEnd = windowEndMs + WINDOW_PADDING_MS;
    String windowStart = URLEncoder.encode(
        Instant.ofEpochMilli(qStart).toString(), StandardCharsets.UTF_8);
    String windowEnd = URLEncoder.encode(
        Instant.ofEpochMilli(qEnd).toString(), StandardCharsets.UTF_8);
    String query = "windowStart=" + windowStart + "&windowEnd=" + windowEnd + "&topN=10";
    String url = "http://" + host + ":" + httpPort + "/api/metrics?" + query;

    System.out.println();
    System.out.println("===== Metrics API (" + phase + ") =====");
    System.out.println(
        "phase wall: "
            + Instant.ofEpochMilli(windowStartMs)
            + " .. "
            + Instant.ofEpochMilli(windowEndMs)
            + " | query window (padded): "
            + Instant.ofEpochMilli(qStart)
            + " .. "
            + Instant.ofEpochMilli(qEnd));

    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .build();
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(60))
          .GET()
          .build();
      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

      String body = resp.body();
      String path = resultsDir + "/metrics-" + phase + "-api.json";
      Files.writeString(Path.of(path), body, StandardCharsets.UTF_8);
      System.out.println("HTTP " + resp.statusCode() + " | saved: " + path);

      if (resp.statusCode() != 200) {
        System.out.println("Metrics API non-200; check server and rate limiter.");
      }

      if (body.length() <= PREVIEW_CHARS) {
        System.out.println(body);
      } else {
        System.out.println(body.substring(0, PREVIEW_CHARS));
        System.out.println("... (" + body.length() + " chars total; full JSON in " + path + ")");
      }
    } catch (Exception e) {
      System.out.println("Metrics API failed: " + e.getMessage());
      System.out.println("Fix: server on " + httpPort + ", or run: curl -s '" + url + "'");
    }
  }
}
