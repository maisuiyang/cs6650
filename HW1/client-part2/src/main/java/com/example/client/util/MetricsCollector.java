package com.example.client.util;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {

  private final long phaseStartMs;
  /** Per-room / type / time bucket counts when an ACK is processed (latency path). */
  private final ConcurrentHashMap<Integer, AtomicLong> roomCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> typeCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, AtomicLong> bucketCounts = new ConcurrentHashMap<>();
  /** Same dimensions, counted on successful send (matches Success / wall time). */
  private final ConcurrentHashMap<Integer, AtomicLong> sendRoomCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> sendTypeCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, AtomicLong> sendBucketCounts = new ConcurrentHashMap<>();

  public MetricsCollector(long phaseStartMs) {
    this.phaseStartMs = phaseStartMs;
  }

  public void recordSend(long epochMs, int roomId, String messageType) {
    sendRoomCounts.computeIfAbsent(roomId, k -> new AtomicLong()).incrementAndGet();
    if (messageType != null && !messageType.isEmpty()) {
      sendTypeCounts.computeIfAbsent(messageType, k -> new AtomicLong()).incrementAndGet();
    }
    long bucketStart = ((epochMs - phaseStartMs) / 10_000L) * 10_000L + phaseStartMs;
    sendBucketCounts.computeIfAbsent(bucketStart, k -> new AtomicLong()).incrementAndGet();
  }

  public void recordAck(long epochMs, String roomId, String messageType) {
    int room = parseRoom(roomId);
    if (room >= 0) {
      roomCounts.computeIfAbsent(room, k -> new AtomicLong()).incrementAndGet();
    }
    if (messageType != null && !messageType.isEmpty()) {
      typeCounts.computeIfAbsent(messageType, k -> new AtomicLong()).incrementAndGet();
    }
    long bucketStart = ((epochMs - phaseStartMs) / 10_000L) * 10_000L + phaseStartMs;
    bucketCounts.computeIfAbsent(bucketStart, k -> new AtomicLong()).incrementAndGet();
  }

  public Map<Integer, Long> snapshotRoomCounts() {
    Map<Integer, Long> out = new TreeMap<>();
    for (Map.Entry<Integer, AtomicLong> e : roomCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  public Map<String, Long> snapshotTypeCounts() {
    Map<String, Long> out = new TreeMap<>();
    for (Map.Entry<String, AtomicLong> e : typeCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  public Map<Long, Long> snapshotBucketCounts() {
    Map<Long, Long> out = new TreeMap<>();
    for (Map.Entry<Long, AtomicLong> e : bucketCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  public Map<Integer, Long> snapshotSendRoomCounts() {
    Map<Integer, Long> out = new TreeMap<>();
    for (Map.Entry<Integer, AtomicLong> e : sendRoomCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  public Map<String, Long> snapshotSendTypeCounts() {
    Map<String, Long> out = new TreeMap<>();
    for (Map.Entry<String, AtomicLong> e : sendTypeCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  public Map<Long, Long> snapshotSendBucketCounts() {
    Map<Long, Long> out = new TreeMap<>();
    for (Map.Entry<Long, AtomicLong> e : sendBucketCounts.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return out;
  }

  private int parseRoom(String roomId) {
    if (roomId == null || roomId.isEmpty()) return -1;
    try {
      return Integer.parseInt(roomId);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
