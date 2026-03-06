package com.example.client.sender;

import com.example.client.model.OutboundMessage;
import com.example.client.util.CsvWriter;
import com.example.client.util.LatencyTracker;
import com.example.client.util.MetricsCollector;
import com.example.client.util.Stats;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SenderWorker implements Runnable {

  private final BlockingQueue<OutboundMessage> queue;
  private final Stats stats;
  private final URI uri;
  private final LatencyTracker tracker;
  private final CsvWriter csv;
  private final MetricsCollector metrics;
  private final Map<Integer, WsClient> clientsByRoom = new HashMap<>();

  public SenderWorker(BlockingQueue<OutboundMessage> queue, Stats stats, URI uri, LatencyTracker tracker, CsvWriter csv, MetricsCollector metrics) {
    this.queue = queue;
    this.stats = stats;
    this.uri = uri;
    this.tracker = tracker;
    this.csv = csv;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    while (true) {
      OutboundMessage msg;
      try {
        msg = queue.poll(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }

      if (msg == null) {
        break;
      }

      boolean ok = safeSend(msg);
      if (ok) stats.success.incrementAndGet();
      else stats.failed.incrementAndGet();
    }

    for (WsClient c : clientsByRoom.values()) {
      try { c.close(); } catch (Exception ignored) {}
    }
  }

  private WsClient connect(int roomId) {
    URI roomUri = buildRoomUri(roomId);
    while (true) {
      try {
        CountDownLatch latch = new CountDownLatch(1);
        WsClient client = new WsClient(roomUri, stats, latch, tracker, csv, metrics);
        client.connect();
        latch.await(5, TimeUnit.SECONDS);

        if (client.isOpen()) return client;
      } catch (Exception ignored) {}

      stats.reconnections.incrementAndGet();
      sleepMs(200);
    }
  }

  private boolean safeSend(OutboundMessage msg) {
    WsClient client = clientsByRoom.get(msg.roomId);
    if (client == null || !client.isOpen()) {
      stats.reconnections.incrementAndGet();
      client = connect(msg.roomId);
      clientsByRoom.put(msg.roomId, client);
    }

    for (int attempt = 1; attempt <= 5; attempt++) {
      try {
        if (!client.isOpen()) {
          stats.reconnections.incrementAndGet();
          client = connect(msg.roomId);
          clientsByRoom.put(msg.roomId, client);
        }
        tracker.markSend(msg.id, System.nanoTime());
        client.send(msg.json);
        return true;
      } catch (Exception e) {
        if (attempt < 5) {
          stats.retries.incrementAndGet();
          sleepMs(backoffMs(attempt));
        }
      }
    }
    tracker.abandon(msg.id);
    return false;
  }

  private void sleepMs(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
  }

  private URI buildRoomUri(int roomId) {
    return URI.create(uri.toString().replaceFirst("/chat/\\d+$", "/chat/" + roomId));
  }

  private long backoffMs(int attempt) {
    long base = 50L;
    long delay = base * (1L << (attempt - 1));
    return Math.min(delay, 2000L);
  }
}
