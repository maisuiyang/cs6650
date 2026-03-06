package com.example.client;

import com.example.client.generator.MessageGenerator;
import com.example.client.model.OutboundMessage;
import com.example.client.sender.SenderWorker;
import com.example.client.util.CsvWriter;
import com.example.client.util.LatencyTracker;
import com.example.client.util.MetricsCollector;
import com.example.client.util.Stats;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.*;

public class Main {

  public static void main(String[] args) throws Exception {
    String host = "54.189.65.140";
    int port = 8081;

    // Warmup：32 threads × 1000 msg = 32000
    runPhase("WARMUP", host, port, 32, 32_000);

    runPhase("MAIN", host, port, 32, 500_000);
  }

  private static void runPhase(String phase, String host, int port, int workers, int totalMsgs) throws Exception {
    System.out.println("===== " + phase + " START =====");

    Stats stats = new Stats();
    LatencyTracker tracker = new LatencyTracker();

    String resultsDir = "results";
    Files.createDirectories(Paths.get(resultsDir));
    String csvPath = resultsDir + "/" + phase + "-latency.csv";
    CsvWriter csv = new CsvWriter(csvPath);

    BlockingQueue<OutboundMessage> queue = new LinkedBlockingQueue<>(100_000);

    Thread gen = new Thread(new MessageGenerator(queue, totalMsgs));
    gen.start();

    ExecutorService pool = Executors.newFixedThreadPool(workers);
    long start = System.currentTimeMillis();
    MetricsCollector metrics = new MetricsCollector(start);

    for (int i = 0; i < workers; i++) {
      URI uri = new URI("ws://" + host + ":" + port + "/chat/1");
      pool.submit(new SenderWorker(queue, stats, uri, tracker, csv, metrics));
    }

    gen.join();

    pool.shutdown();
    pool.awaitTermination(60, TimeUnit.MINUTES);

    long end = System.currentTimeMillis();

    csv.close();

    stats.printSummary(phase, start, end);

    long[] arr = tracker.snapshot();
    Arrays.sort(arr);

    System.out.println("CSV saved to: " + csvPath);
    System.out.println("Latency count: " + arr.length);
    System.out.println("mean(ms): " + mean(arr));
    System.out.println("median(ms): " + percentile(arr, 50));
    System.out.println("p50(ms): " + percentile(arr, 50));
    System.out.println("p95(ms): " + percentile(arr, 95));
    System.out.println("p99(ms): " + percentile(arr, 99));
    System.out.println("min(ms): " + (arr.length == 0 ? 0 : arr[0]));
    System.out.println("max(ms): " + (arr.length == 0 ? 0 : arr[arr.length - 1]));

    printRoomThroughput(metrics.snapshotRoomCounts(), start, end);
    printTypeDistribution(metrics.snapshotTypeCounts());
    writeThroughputCsv(resultsDir + "/" + phase + "-throughput.csv", metrics.snapshotBucketCounts());

    System.out.println("===== " + phase + " END =====");
  }

  private static long percentile(long[] arr, int p) {
    if (arr.length == 0) return 0;
    int idx = (int) Math.ceil((p / 100.0) * arr.length) - 1;
    if (idx < 0) idx = 0;
    if (idx >= arr.length) idx = arr.length - 1;
    return arr[idx];
  }

  private static long mean(long[] arr) {
    if (arr.length == 0) return 0;
    long sum = 0;
    for (long v : arr) sum += v;
    return sum / arr.length;
  }

  private static void printRoomThroughput(Map<Integer, Long> roomCounts, long startMs, long endMs) {
    double secs = (endMs - startMs) / 1000.0;
    System.out.println("Throughput per room (msg/s):");
    for (Map.Entry<Integer, Long> e : roomCounts.entrySet()) {
      double tps = secs > 0 ? e.getValue() / secs : 0;
      System.out.println("room " + e.getKey() + ": " + tps);
    }
  }

  private static void printTypeDistribution(Map<String, Long> typeCounts) {
    System.out.println("Message type distribution:");
    for (Map.Entry<String, Long> e : typeCounts.entrySet()) {
      System.out.println(e.getKey() + ": " + e.getValue());
    }
  }

  private static void writeThroughputCsv(String path, Map<Long, Long> buckets) throws Exception {
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
      bw.write("timestamp,count\n");
      for (Map.Entry<Long, Long> e : buckets.entrySet()) {
        bw.write(e.getKey() + "," + e.getValue() + "\n");
      }
    }
  }
}
