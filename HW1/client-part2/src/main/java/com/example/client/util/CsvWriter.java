package com.example.client.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CsvWriter implements AutoCloseable {

  private final BufferedWriter bw;

  public CsvWriter(String path) throws IOException {
    this.bw = new BufferedWriter(new FileWriter(path));
    bw.write("timestamp,messageType,latency,statusCode,roomId\n");
  }

  public synchronized void writeLine(long epochMs, String roomId, String messageType, String status, long latencyMs) {
    try {
      bw.write(epochMs + "," + messageType + "," + latencyMs + "," + status + "," + roomId + "\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    bw.flush();
    bw.close();
  }
}
