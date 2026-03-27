package com.example.consumer.persistence;

import com.example.consumer.model.QueueMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class BatchedMessagePersistenceWorker {

  private static final Logger log = LoggerFactory.getLogger(BatchedMessagePersistenceWorker.class);

  private static final String INSERT_MESSAGE =
      "INSERT INTO messages (message_id, room_id, user_id, username, body, message_type, server_id, client_ip, sent_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (message_id) DO NOTHING";

  private static final String UPSERT_ACTIVITY =
      "INSERT INTO user_room_activity (user_id, room_id, last_active_at) VALUES (?, ?, ?) "
          + "ON CONFLICT (user_id, room_id) DO UPDATE SET "
          + "last_active_at = GREATEST(user_room_activity.last_active_at, EXCLUDED.last_active_at)";

  private final BlockingQueue<QueueMessage> pending;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final MessageDlqPublisher dlqPublisher;
  private final CircuitBreaker circuitBreaker;
  private final ExecutorService databaseWriterExecutor;

  private final int batchSize;
  private final long flushIntervalMs;
  private final int maxRetries;

  private volatile boolean running = true;

  public BatchedMessagePersistenceWorker(
      JdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate,
      MessageDlqPublisher dlqPublisher,
      CircuitBreakerRegistry circuitBreakerRegistry,
      ExecutorService databaseWriterExecutor,
      @Value("${chat.persistence.batch-size:500}") int batchSize,
      @Value("${chat.persistence.flush-interval-ms:500}") long flushIntervalMs,
      @Value("${chat.persistence.queue-capacity:100000}") int queueCapacity,
      @Value("${chat.persistence.max-retries:4}") int maxRetries) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
    this.dlqPublisher = dlqPublisher;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("databaseWrites");
    this.databaseWriterExecutor = databaseWriterExecutor;
    this.batchSize = Math.max(1, batchSize);
    this.flushIntervalMs = Math.max(50L, flushIntervalMs);
    this.maxRetries = Math.max(1, maxRetries);
    this.pending = new LinkedBlockingQueue<>(queueCapacity);
  }

  public boolean enqueue(QueueMessage message) {
    return pending.offer(message);
  }

  @PostConstruct
  public void startWorker() {
    databaseWriterExecutor.execute(this::runLoop);
  }

  @PreDestroy
  public void stopWorker() {
    running = false;
  }

  private void runLoop() {
    while (running) {
      try {
        QueueMessage first = pending.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
        if (first == null) {
          continue;
        }
        List<QueueMessage> batch = new ArrayList<>();
        batch.add(first);
        pending.drainTo(batch, batchSize - 1);
        flushWithRetry(batch);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Persistence loop error: {}", e.getMessage(), e);
      }
    }
  }

  private void flushWithRetry(List<QueueMessage> batch) {
    long delayMs = 50L;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        circuitBreaker.executeRunnable(() -> persistBatch(batch));
        return;
      } catch (CallNotPermittedException e) {
        log.warn("Database circuit open; sending batch to DLQ");
        dlqPublisher.publishFailedBatch(batch, "database-circuit-open");
        return;
      } catch (Exception e) {
        log.warn("Batch write attempt {} failed: {}", attempt + 1, e.getMessage());
        if (attempt == maxRetries - 1) {
          dlqPublisher.publishFailedBatch(batch, e.getMessage());
          return;
        }
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          dlqPublisher.publishFailedBatch(batch, "interrupted");
          return;
        }
        delayMs = Math.min(delayMs * 2, 10_000L);
      }
    }
  }

  private void persistBatch(List<QueueMessage> batch) {
    transactionTemplate.executeWithoutResult(
        status -> {
          for (QueueMessage m : batch) {
            UUID id = UUID.fromString(m.getMessageId());
            Instant sentAt = parseSentAt(m);
            jdbcTemplate.update(
                INSERT_MESSAGE,
                id,
                m.getRoomId(),
                m.getUserId(),
                m.getUsername(),
                m.getMessage(),
                m.getMessageType(),
                m.getServerId(),
                m.getClientIp(),
                Timestamp.from(sentAt));
            jdbcTemplate.update(
                UPSERT_ACTIVITY,
                m.getUserId(),
                m.getRoomId(),
                Timestamp.from(sentAt));
          }
        });
  }

  private static Instant parseSentAt(QueueMessage m) {
    try {
      if (m.getTimestamp() != null && !m.getTimestamp().isBlank()) {
        return Instant.parse(m.getTimestamp());
      }
    } catch (Exception ignored) {
    }
    return Instant.now();
  }
}
