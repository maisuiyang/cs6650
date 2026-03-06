package com.example.client.util;

import java.util.concurrent.atomic.AtomicLong;

public class Stats {
  public final AtomicLong success = new AtomicLong(0);
  public final AtomicLong failed = new AtomicLong(0);
  public final AtomicLong retries = new AtomicLong(0);
  public final AtomicLong connections = new AtomicLong(0);
  public final AtomicLong reconnections = new AtomicLong(0);

  public void printSummary(String phaseName, long startMs, long endMs) {
    long totalMs = endMs - startMs;
    double secs = totalMs / 1000.0;

    long ok = success.get();
    long bad = failed.get();
    double tps = secs > 0 ? ok / secs : 0;

    System.out.println("===== " + phaseName + " RESULT =====");
    System.out.println("Success: " + ok);
    System.out.println("Failed: " + bad);
    System.out.println("Retries: " + retries.get());
    System.out.println("Wall time (s): " + secs);
    System.out.println("Throughput (msg/s): " + tps);
    System.out.println("Connections: " + connections.get());
    System.out.println("Reconnections: " + reconnections.get());
  }
}