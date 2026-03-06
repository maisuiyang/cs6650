package com.example.client.util;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {

  private final long phaseStartMs;
  private final ConcurrentHashMap<Integer, AtomicLong> roomCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> typeCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, AtomicLong> bucketCounts = new ConcurrentHashMap<>();

  public MetricsCollector(long phaseStartMs) {
    this.phaseStartMs = phaseStartMs;
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

  private int parseRoom(String roomId) {
    if (roomId == null || roomId.isEmpty()) return -1;
    try {
      return Integer.parseInt(roomId);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
