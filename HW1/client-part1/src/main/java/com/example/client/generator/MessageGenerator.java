package com.example.client.generator;

import com.example.client.model.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class MessageGenerator implements Runnable {

  private final BlockingQueue<OutboundMessage> queue;
  private final int totalMessages;

  private static final ObjectMapper mapper = new ObjectMapper();
  private final Random rand = new Random();
  private final AtomicLong seq = new AtomicLong(1);
  private static final String[] MESSAGE_POOL = buildMessagePool();

  public MessageGenerator(BlockingQueue<OutboundMessage> queue, int totalMessages) {
    this.queue = queue;
    this.totalMessages = totalMessages;
  }

  @Override
  public void run() {
    for (int i = 0; i < totalMessages; i++) {
      try {
        queue.put(generateOne());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private OutboundMessage generateOne() {
    long id = seq.getAndIncrement();

    int userId = 1 + rand.nextInt(100000);
    String username = "user" + userId;
    int roomId = 1 + rand.nextInt(20);

    String type;
    int p = rand.nextInt(100);
    if (p < 90) type = "TEXT";
    else if (p < 95) type = "JOIN";
    else type = "LEAVE";

    ObjectNode node = mapper.createObjectNode();
    node.put("userId", String.valueOf(userId));
    node.put("username", username);
    node.put("message", MESSAGE_POOL[rand.nextInt(MESSAGE_POOL.length)] + "-" + id);
    node.put("timestamp", Instant.now().toString());
    node.put("messageType", type);

    return new OutboundMessage(id, roomId, type, node.toString());
  }

  private static String[] buildMessagePool() {
    String[] pool = new String[50];
    for (int i = 0; i < pool.length; i++) {
      pool[i] = "msg-" + (i + 1);
    }
    return pool;
  }
}
