package com.example.client.model;

public class OutboundMessage {
  public final long id;
  public final int roomId;
  public final String messageType;
  public final String json;

  public OutboundMessage(long id, int roomId, String messageType, String json) {
    this.id = id;
    this.roomId = roomId;
    this.messageType = messageType;
    this.json = json;
  }
}
