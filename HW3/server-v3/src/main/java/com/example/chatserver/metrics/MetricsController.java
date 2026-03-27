package com.example.chatserver.metrics;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsController {

  private final MetricsQueryService metricsQueryService;

  public MetricsController(MetricsQueryService metricsQueryService) {
    this.metricsQueryService = metricsQueryService;
  }

  @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
  @RateLimiter(name = "metricsApi")
  public Map<String, Object> metrics(
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String windowStart,
      @RequestParam(required = false) String windowEnd,
      @RequestParam(required = false, defaultValue = "10") int topN) {

    Instant end = parseWindowInstant(windowEnd, Instant.now());
    Instant start = parseWindowInstant(windowStart, end.minus(1, ChronoUnit.HOURS));
    if (topN < 1 || topN > 100) {
      topN = 10;
    }
    return metricsQueryService.buildMetrics(start, end, topN, roomId, userId);
  }

  /**
   * Accepts ISO-8601 instants ({@code ...Z}), offset datetimes, or naive local datetimes (interpreted as UTC)
   * so manual {@code curl} without a trailing {@code Z} does not return 500.
   */
  private static Instant parseWindowInstant(String value, Instant fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String trimmed = value.trim();
    try {
      return Instant.parse(trimmed);
    } catch (DateTimeParseException ignored) {
      try {
        return OffsetDateTime.parse(trimmed).toInstant();
      } catch (DateTimeParseException ignored2) {
        return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atOffset(ZoneOffset.UTC)
            .toInstant();
      }
    }
  }
}
