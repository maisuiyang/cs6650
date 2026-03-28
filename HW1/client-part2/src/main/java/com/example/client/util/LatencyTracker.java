package com.example.client.util;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LatencyTracker {

  private final ConcurrentHashMap<Long, Long> sendNs = new ConcurrentHashMap<>();
  private final int sampleEveryN;
  private final long staleNs;
  private final AtomicLong staleEvicted = new AtomicLong();

  private long[] latenciesMs = new long[1 << 20];
  private int size = 0;

  public LatencyTracker() {
    this(1, 30_000);
  }

  public LatencyTracker(int sampleEveryN, long staleMs) {
    this.sampleEveryN = Math.max(1, sampleEveryN);
    this.staleNs = Math.max(1_000L, staleMs) * 1_000_000L;
  }

  public void markSend(long id, long nowNs) {
    if ((id % sampleEveryN) != 0) {
      return;
    }
    sendNs.put(id, nowNs);
  }

  public Long onAck(long id, long nowNs) {
    Long start = sendNs.remove(id);
    if (start == null) return null;
    long ms = (nowNs - start) / 1_000_000L;
    addLatency(ms);
    return ms;
  }

  public void abandon(long id) {
    sendNs.remove(id);
  }

  public int pendingCount() {
    evictStale(System.nanoTime());
    return sendNs.size();
  }

  public long staleEvictedCount() {
    return staleEvicted.get();
  }

  private void evictStale(long nowNs) {
    for (var entry : sendNs.entrySet()) {
      if (nowNs - entry.getValue() > staleNs) {
        if (sendNs.remove(entry.getKey(), entry.getValue())) {
          staleEvicted.incrementAndGet();
        }
      }
    }
  }

  private synchronized void addLatency(long ms) {
    if (size >= latenciesMs.length) {
      latenciesMs = Arrays.copyOf(latenciesMs, latenciesMs.length * 2);
    }
    latenciesMs[size++] = ms;
  }

  public synchronized long[] snapshot() {
    return Arrays.copyOf(latenciesMs, size);
  }
}
