package com.example.consumer.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Profile("!test")
public class MinuteAnalyticsRefresher {

  private final JdbcTemplate jdbcTemplate;

  public MinuteAnalyticsRefresher(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Scheduled(fixedDelayString = "${chat.stats.period-ms:60000}")
  public void refreshMinuteBuckets() {
    Instant until = Instant.now();
    Instant since = until.minus(24, ChronoUnit.HOURS);
    jdbcTemplate.update(
        "INSERT INTO analytics_minute (bucket_start, message_count) "
            + "SELECT date_trunc('minute', sent_at), COUNT(*) FROM messages "
            + "WHERE sent_at >= ? AND sent_at < ? "
            + "GROUP BY date_trunc('minute', sent_at) "
            + "ON CONFLICT (bucket_start) DO UPDATE SET message_count = EXCLUDED.message_count",
        Timestamp.from(since),
        Timestamp.from(until));
  }
}
