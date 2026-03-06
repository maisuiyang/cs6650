package com.example.client.sender;

import com.example.client.util.Stats;
import com.example.client.util.CsvWriter;
import com.example.client.util.LatencyTracker;
import com.example.client.util.MetricsCollector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class WsClient extends WebSocketClient {

  private final Stats stats;
  private final CountDownLatch openLatch;
  private final AtomicBoolean openFlag = new AtomicBoolean(false);
  private static final ObjectMapper mapper = new ObjectMapper();
  private final LatencyTracker tracker;
  private final CsvWriter csv;
  private final MetricsCollector metrics;

  public WsClient(URI serverUri, Stats stats, CountDownLatch openLatch, LatencyTracker tracker, CsvWriter csv, MetricsCollector metrics) {
    super(serverUri);
    this.stats = stats;
    this.openLatch = openLatch;
    this.tracker = tracker;
    this.csv = csv;
    this.metrics = metrics;
  }

  @Override
  public void onOpen(ServerHandshake handshakedata) {
    openFlag.set(true);
    stats.connections.incrementAndGet();
    openLatch.countDown();
  }

  @Override
  public void onMessage(String message) {
    try {
      JsonNode root = mapper.readTree(message);
      String status = root.path("status").asText("");
      String roomId = root.path("roomId").asText("");

      JsonNode orig = root.path("originalMessage");
      String msgType = orig.path("messageType").asText("");
      String msgContent = orig.path("message").asText("");
      
      // Extract ID from message (format: "msg-X-ID")
      String[] parts = msgContent.split("-");
      if (parts.length < 3) return;
      
      long id;
      try {
        id = Long.parseLong(parts[parts.length - 1]);
      } catch (NumberFormatException e) {
        return;
      }
      long nowNs = System.nanoTime();

      Long latencyMs = tracker.onAck(id, nowNs);
      if (latencyMs == null) return;

      long epochMs = System.currentTimeMillis();
      csv.writeLine(epochMs, roomId, msgType, status, latencyMs);
      metrics.recordAck(epochMs, roomId, msgType);
    } catch (Exception ignored) {
    }
  }


  @Override
  public void onClose(int code, String reason, boolean remote) {
    openFlag.set(false);
  }

  @Override
  public void onError(Exception ex) {
  }

  public boolean isOpenFlag() {
    return openFlag.get();
  }
}