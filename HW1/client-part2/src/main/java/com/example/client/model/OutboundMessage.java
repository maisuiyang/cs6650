package com.example.client.model;

public class OutboundMessage {

  /** Sentinel: workers exit when received (one per worker after generation completes). */
  public static final OutboundMessage POISON = new OutboundMessage(-1, -1, "POISON", "");

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
