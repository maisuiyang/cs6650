package com.example.chatserver.metrics;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsQueryService {

  private final JdbcTemplate jdbcTemplate;

  public MetricsQueryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Cacheable(
      value = "metricsSummary",
      key = "#windowStart.toString() + '-' + #windowEnd.toString() + '-' + #topN + '-' + (#roomId != null ? #roomId : '') + '-' + (#userId != null ? #userId : '')"
  )
  public Map<String, Object> buildMetrics(
      Instant windowStart,
      Instant windowEnd,
      int topN,
      String roomId,
      String userId) {

    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> core = new LinkedHashMap<>();
    Map<String, Object> analytics = new LinkedHashMap<>();

    core.put("roomMessages", queryRoomMessages(roomId, windowStart, windowEnd));
    core.put("userMessageHistory", queryUserHistory(userId, windowStart, windowEnd));
    core.put("activeUsersInWindow", queryActiveUserCount(windowStart, windowEnd));
    core.put("roomsForUser", queryUserRooms(userId));

    analytics.put("messagesPerMinute", queryMessagesPerMinute(windowStart, windowEnd));
    analytics.put("topUsers", queryTopUsers(windowStart, windowEnd, topN));
    analytics.put("topRooms", queryTopRooms(windowStart, windowEnd, topN));
    analytics.put("participationPatterns", queryParticipationPatterns(windowStart, windowEnd));

    root.put("core", core);
    root.put("analytics", analytics);
    root.put("windowStart", windowStart.toString());
    root.put("windowEnd", windowEnd.toString());
    return root;
  }

  private Map<String, Object> queryRoomMessages(String roomId, Instant start, Instant end) {
    Map<String, Object> section = new LinkedHashMap<>();
    if (roomId == null || roomId.isBlank()) {
      section.put("note", "roomId query parameter was not provided");
      section.put("messages", List.of());
      return section;
    }
    String sql =
        "SELECT CAST(message_id AS VARCHAR(64)) AS message_id, room_id, user_id, username, body, message_type, server_id, client_ip, sent_at "
            + "FROM messages WHERE room_id = ? AND sent_at >= ? AND sent_at <= ? ORDER BY sent_at ASC LIMIT 2000";
    List<Map<String, Object>> rows =
        jdbcTemplate.query(sql, messageRowMapper(), roomId, Timestamp.from(start), Timestamp.from(end));
    section.put("roomId", roomId);
    section.put("count", rows.size());
    section.put("messages", rows);
    return section;
  }

  private Map<String, Object> queryUserHistory(String uid, Instant start, Instant end) {
    Map<String, Object> section = new LinkedHashMap<>();
    if (uid == null || uid.isBlank()) {
      section.put("note", "userId query parameter was not provided");
      section.put("messages", List.of());
      return section;
    }
    String sql =
        "SELECT CAST(message_id AS VARCHAR(64)) AS message_id, room_id, user_id, username, body, message_type, server_id, client_ip, sent_at "
            + "FROM messages WHERE user_id = ? AND sent_at >= ? AND sent_at <= ? ORDER BY sent_at DESC LIMIT 2000";
    List<Map<String, Object>> rows =
        jdbcTemplate.query(sql, messageRowMapper(), uid, Timestamp.from(start), Timestamp.from(end));
    section.put("userId", uid);
    section.put("count", rows.size());
    section.put("messages", rows);
    return section;
  }

  private Map<String, Object> queryActiveUserCount(Instant start, Instant end) {
    String sql = "SELECT COUNT(DISTINCT user_id) FROM messages WHERE sent_at >= ? AND sent_at <= ?";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(start), Timestamp.from(end));
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("uniqueUserCount", count != null ? count : 0L);
    return section;
  }

  private Map<String, Object> queryUserRooms(String uid) {
    Map<String, Object> section = new LinkedHashMap<>();
    if (uid == null || uid.isBlank()) {
      section.put("note", "userId query parameter was not provided for roomsForUser");
      section.put("rooms", List.of());
      return section;
    }
    String sql =
        "SELECT room_id, last_active_at FROM user_room_activity WHERE user_id = ? ORDER BY last_active_at DESC LIMIT 500";
    List<Map<String, Object>> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("roomId", rs.getString("room_id"));
              m.put("lastActiveAt", toInstantString(rs, "last_active_at"));
              return m;
            },
            uid);
    section.put("userId", uid);
    section.put("rooms", rows);
    return section;
  }

  private List<Map<String, Object>> queryMessagesPerMinute(Instant start, Instant end) {
    String rollup =
        "SELECT bucket_start, message_count AS cnt FROM analytics_minute "
            + "WHERE bucket_start >= ? AND bucket_start <= ? ORDER BY bucket_start DESC LIMIT 120";
    List<Map<String, Object>> fromRollup =
        jdbcTemplate.query(
            rollup,
            (rs, rowNum) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("minute", rs.getTimestamp("bucket_start").toInstant().toString());
              m.put("messageCount", rs.getLong("cnt"));
              return m;
            },
            Timestamp.from(start),
            Timestamp.from(end));
    if (!fromRollup.isEmpty()) {
      return fromRollup;
    }
    String sql =
        "SELECT date_trunc('minute', sent_at) AS bucket, COUNT(*) AS cnt "
            + "FROM messages WHERE sent_at >= ? AND sent_at <= ? "
            + "GROUP BY 1 ORDER BY 1 DESC LIMIT 120";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("minute", rs.getTimestamp("bucket").toInstant().toString());
          m.put("messageCount", rs.getLong("cnt"));
          return m;
        },
        Timestamp.from(start),
        Timestamp.from(end));
  }

  private List<Map<String, Object>> queryTopUsers(Instant start, Instant end, int topN) {
    String sql =
        "SELECT user_id, COUNT(*) AS cnt FROM messages WHERE sent_at >= ? AND sent_at <= ? "
            + "GROUP BY user_id ORDER BY cnt DESC LIMIT ?";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("userId", rs.getString("user_id"));
          m.put("messageCount", rs.getLong("cnt"));
          return m;
        },
        Timestamp.from(start),
        Timestamp.from(end),
        topN);
  }

  private List<Map<String, Object>> queryTopRooms(Instant start, Instant end, int topN) {
    String sql =
        "SELECT room_id, COUNT(*) AS cnt FROM messages WHERE sent_at >= ? AND sent_at <= ? "
            + "GROUP BY room_id ORDER BY cnt DESC LIMIT ?";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("roomId", rs.getString("room_id"));
          m.put("messageCount", rs.getLong("cnt"));
          return m;
        },
        Timestamp.from(start),
        Timestamp.from(end),
        topN);
  }

  private List<Map<String, Object>> queryParticipationPatterns(Instant start, Instant end) {
    String sql =
        "SELECT user_id, date_trunc('day', sent_at) AS day_bucket, COUNT(*) AS cnt "
            + "FROM messages WHERE sent_at >= ? AND sent_at <= ? "
            + "GROUP BY user_id, day_bucket ORDER BY day_bucket DESC, cnt DESC LIMIT 500";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("userId", rs.getString("user_id"));
          m.put("day", rs.getTimestamp("day_bucket").toInstant().toString());
          m.put("messageCount", rs.getLong("cnt"));
          return m;
        },
        Timestamp.from(start),
        Timestamp.from(end));
  }

  private RowMapper<Map<String, Object>> messageRowMapper() {
    return (ResultSet rs, int rowNum) -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("messageId", rs.getString("message_id"));
      m.put("roomId", rs.getString("room_id"));
      m.put("userId", rs.getString("user_id"));
      m.put("username", rs.getString("username"));
      m.put("message", rs.getString("body"));
      m.put("messageType", rs.getString("message_type"));
      m.put("serverId", rs.getString("server_id"));
      m.put("clientIp", rs.getString("client_ip"));
      m.put("sentAt", toInstantString(rs, "sent_at"));
      return m;
    };
  }

  private static String toInstantString(ResultSet rs, String column) throws SQLException {
    Timestamp ts = rs.getTimestamp(column);
    if (ts == null) {
      return null;
    }
    return ts.toInstant().toString();
  }
}
