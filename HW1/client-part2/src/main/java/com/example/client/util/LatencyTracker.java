package com.example.client.util;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class LatencyTracker {

  private final ConcurrentHashMap<Long, Long> sendNs = new ConcurrentHashMap<>();

  private long[] latenciesMs = new long[1 << 20];
  private int size = 0;

  public void markSend(long id, long nowNs) {
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
