package com.example.client;

import com.example.client.generator.MessageGenerator;
import com.example.client.model.OutboundMessage;
import com.example.client.sender.SenderWorker;
import com.example.client.util.CsvWriter;
import com.example.client.util.LatencyTracker;
import com.example.client.util.MetricsApiClient;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

  /*
   * Phase switches (Assignment 3 load tests):
   * - Full run: WARMUP + BASELINE + MAIN + (optional) ENDURANCE.
   * - Endurance only: WARMUP/BASELINE/MAIN false; ENDURANCE true; Ctrl+C when done; then curl metrics.
   */

  /** Set true for faster batch/flush experiments (100k MAIN); false for full stress (1M MAIN). */
  private static final boolean QUICK_MAIN_FOR_BATCH_TUNING = false;

  private static final int MAIN_MESSAGE_COUNT = QUICK_MAIN_FOR_BATCH_TUNING ? 100_000 : 1_000_000;

  private static final boolean RUN_WARMUP_PHASE = true;

  /** Assignment 3 Test 1: Baseline 500,000 messages. */
  private static final boolean RUN_BASELINE_PHASE = true;
  private static final int BASELINE_MESSAGE_COUNT = 500_000;

  /** Assignment 3 Test 2: Stress 1,000,000 messages. Set false when running endurance-only. */
  private static final boolean RUN_MAIN_PHASE = true;

  /**
   * Assignment 3 Test 3: Endurance — sustained load. ~26 workers ~= 80% of 32.
   * Cap is large; stop with Ctrl+C after your target duration, then curl /api/metrics for the window.
   */
  private static final boolean RUN_ENDURANCE_PHASE = false;
  private static final int ENDURANCE_WORKERS = 26;
  /** High cap so the phase does not finish before you stop it. */
  private static final int ENDURANCE_MESSAGE_CAP = 50_000_000;

  /** After each phase, GET /api/metrics and save results/metrics-&lt;phase&gt;-api.json (screenshot for report). */
  private static final boolean FETCH_METRICS_API_AFTER_PHASE = true;
  private static final long ACK_DRAIN_MAX_WAIT_MS = 30_000;
  private static final long ACK_DRAIN_POLL_MS = 200;
  /** After generator finishes, wait until the work queue is empty (all messages taken by workers). */
  private static final long QUEUE_DRAIN_MAX_WAIT_MS = 15 * 60_000L;
  private static final long QUEUE_DRAIN_POLL_MS = 200;
  /** Wait until success count reaches totalMsgs (queue can be empty while sends still in flight). */
  private static final long SEND_COMPLETION_MAX_WAIT_MS = 10 * 60_000L;
  /** After this, allow tiny gap (stuck WebSocket send) so the phase can finish. */
  private static final long SEND_COMPLETION_SOFT_WAIT_MS = 45_000L;
  /** If success is within this many of expected after soft wait, continue (report the gap). */
  private static final int SEND_COMPLETION_MAX_GAP = 128;
  private static final long METRICS_DELAY_AFTER_PHASE_MS = 5_000;
  private static final int LATENCY_SAMPLE_EVERY_N = 10;
  private static final long LATENCY_STALE_MS = 15_000;

  public static void main(String[] args) throws Exception {
    // Local server-v3: use 127.0.0.1 and 8081. For AWS ALB again, set host/port to your load balancer.
    String host = "127.0.0.1";
    int port = 8081;

    if (RUN_WARMUP_PHASE) {
      runPhase("WARMUP", host, port, 32, 32_000);
    }

    if (RUN_BASELINE_PHASE) {
      runPhase("BASELINE", host, port, 32, BASELINE_MESSAGE_COUNT);
    }

    if (RUN_MAIN_PHASE) {
      runPhase("MAIN", host, port, 32, MAIN_MESSAGE_COUNT);
    }

    if (RUN_ENDURANCE_PHASE) {
      System.out.println(
          "ENDURANCE: run until your target duration (e.g. 30 min), then press Ctrl+C. "
              + "Afterward: curl /api/metrics with windowStart/windowEnd covering this run.");
      runPhase("ENDURANCE", host, port, ENDURANCE_WORKERS, ENDURANCE_MESSAGE_CAP);
    }
  }

  private static void runPhase(String phase, String host, int port, int workers, int totalMsgs) throws Exception {
    System.out.println("===== " + phase + " START =====");

    Stats stats = new Stats();
    LatencyTracker tracker = new LatencyTracker(LATENCY_SAMPLE_EVERY_N, LATENCY_STALE_MS);

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

    AtomicBoolean phaseRunning = new AtomicBoolean(true);
    ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("client-phase-heartbeat");
      return t;
    });
    heartbeat.scheduleAtFixedRate(
        () -> {
          if (!phaseRunning.get()) {
            return;
          }
          System.out.println(
              "[heartbeat] "
                  + phase
                  + " success="
                  + stats.success.get()
                  + " failed="
                  + stats.failed.get()
                  + " queueSize="
                  + queue.size()
                  + " pendingLatency="
                  + tracker.pendingCount());
        },
        5,
        10,
        TimeUnit.SECONDS);

    gen.join();
    waitForQueueEmpty(phase, queue);
    waitForAllSendsCompleted(phase, stats, totalMsgs);
    waitForAckDrain(phase, queue, tracker);

    if (!queue.isEmpty()) {
      System.out.println(phase + " WARNING: queue not empty before poison (size=" + queue.size() + "); draining...");
      long qDeadline = System.currentTimeMillis() + 60_000L;
      while (!queue.isEmpty() && System.currentTimeMillis() < qDeadline) {
        Thread.sleep(QUEUE_DRAIN_POLL_MS);
      }
    }

    for (int i = 0; i < workers; i++) {
      queue.put(OutboundMessage.POISON);
    }

    pool.shutdown();
    if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
      System.out.println(phase + " pool.awaitTermination timed out; interrupting stuck workers.");
      pool.shutdownNow();
      pool.awaitTermination(30, TimeUnit.SECONDS);
    }

    phaseRunning.set(false);
    heartbeat.shutdown();

    long end = System.currentTimeMillis();

    csv.close();

    stats.printSummary(phase, start, end);

    long[] arr = tracker.snapshot();
    Arrays.sort(arr);

    System.out.println("CSV saved to: " + csvPath);
    System.out.println("Latency sample rate: 1/" + LATENCY_SAMPLE_EVERY_N + ", stale cutoff(ms): " + LATENCY_STALE_MS);
    System.out.println("Stale evicted pending IDs: " + tracker.staleEvictedCount());
    System.out.println("Latency count: " + arr.length);
    System.out.println("mean(ms): " + mean(arr));
    System.out.println("median(ms): " + percentile(arr, 50));
    System.out.println("p50(ms): " + percentile(arr, 50));
    System.out.println("p95(ms): " + percentile(arr, 95));
    System.out.println("p99(ms): " + percentile(arr, 99));
    System.out.println("min(ms): " + (arr.length == 0 ? 0 : arr[0]));
    System.out.println("max(ms): " + (arr.length == 0 ? 0 : arr[arr.length - 1]));

    printRoomThroughputSend(metrics.snapshotSendRoomCounts(), start, end);
    printTypeDistributionSend(metrics.snapshotSendTypeCounts());
    writeThroughputCsv(resultsDir + "/" + phase + "-throughput.csv", metrics.snapshotSendBucketCounts());

    System.out.println("===== " + phase + " END =====");

    if (FETCH_METRICS_API_AFTER_PHASE) {
      Thread.sleep(METRICS_DELAY_AFTER_PHASE_MS);
      MetricsApiClient.fetchAndSave(host, port, phase, start, end, resultsDir);
    }
  }

  private static void waitForAllSendsCompleted(String phase, Stats stats, long expected) throws InterruptedException {
    System.out.println(
        phase + " waiting for success=" + expected + " (or soft completion after "
            + (SEND_COMPLETION_SOFT_WAIT_MS / 1000)
            + "s if gap<=" + SEND_COMPLETION_MAX_GAP + ")...");
    long softAfter = System.currentTimeMillis() + SEND_COMPLETION_SOFT_WAIT_MS;
    long hardDeadline = System.currentTimeMillis() + SEND_COMPLETION_MAX_WAIT_MS;
    while (stats.success.get() < expected) {
      long now = System.currentTimeMillis();
      long got = stats.success.get();
      long gap = expected - got;
      if (gap > 0 && gap <= SEND_COMPLETION_MAX_GAP && now >= softAfter) {
        System.out.println(
            phase + " soft completion: success=" + got + " expected=" + expected + " gap=" + gap
                + " (some sends may be blocked; document in report if needed).");
        return;
      }
      if (now >= hardDeadline) {
        System.out.println(
            phase + " WARNING: success=" + got + " expected=" + expected
                + " after " + (SEND_COMPLETION_MAX_WAIT_MS / 1000) + "s — check server / network.");
        return;
      }
      Thread.sleep(QUEUE_DRAIN_POLL_MS);
    }
  }

  private static void waitForQueueEmpty(String phase, BlockingQueue<OutboundMessage> queue)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + QUEUE_DRAIN_MAX_WAIT_MS;
    while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(QUEUE_DRAIN_POLL_MS);
    }
    if (!queue.isEmpty()) {
      System.out.println(
          phase + " queue drain timeout after " + (QUEUE_DRAIN_MAX_WAIT_MS / 1000)
              + "s; queueSize=" + queue.size() + " (workers may be blocked on send; check server).");
    }
  }

  private static void waitForAckDrain(String phase, BlockingQueue<OutboundMessage> queue, LatencyTracker tracker)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + ACK_DRAIN_MAX_WAIT_MS;
    int stableRounds = 0;
    int lastPending = Integer.MAX_VALUE;

    while (System.currentTimeMillis() < deadline) {
      int pending = tracker.pendingCount();
      boolean queueEmpty = queue.isEmpty();

      if (queueEmpty && pending == 0) {
        return;
      }

      if (queueEmpty && pending == lastPending) {
        stableRounds++;
      } else {
        stableRounds = 0;
      }

      if (queueEmpty && stableRounds >= 10) {
        System.out.println(
            phase + " ACK drain reached steady state with pending=" + pending + "; continuing.");
        return;
      }

      lastPending = pending;
      Thread.sleep(ACK_DRAIN_POLL_MS);
    }

    System.out.println(
        phase + " ACK drain timeout after " + ACK_DRAIN_MAX_WAIT_MS + "ms; pending=" + tracker.pendingCount());
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

  private static void printRoomThroughputSend(Map<Integer, Long> roomCounts, long startMs, long endMs) {
    double secs = (endMs - startMs) / 1000.0;
    System.out.println("Throughput per room (send-side, msg/s):");
    for (Map.Entry<Integer, Long> e : roomCounts.entrySet()) {
      double tps = secs > 0 ? e.getValue() / secs : 0;
      System.out.println("room " + e.getKey() + ": " + tps);
    }
  }

  private static void printTypeDistributionSend(Map<String, Long> typeCounts) {
    System.out.println("Message type distribution (send):");
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
