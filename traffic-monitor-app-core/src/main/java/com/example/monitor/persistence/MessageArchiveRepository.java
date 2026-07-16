package com.example.monitor.persistence;

import com.example.monitor.model.ObservedMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MessageArchiveRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MessageArchiveRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(ObservedMessage message) {
        jdbcTemplate.update(
                "INSERT INTO messages (id, observed_at, transport_protocol, remote_address, local_port, "
                        + "interface_name, message_type, header_json, body_json, payload_size_bytes, "
                        + "payload_text, payload_base64, parse_error) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                message.id(),
                Timestamp.from(message.observedAt()),
                message.transportProtocol(),
                message.remoteAddress(),
                message.localPort(),
                message.interfaceName(),
                message.messageType(),
                toJson(message.header()),
                toJson(message.body()),
                message.payloadSizeBytes(),
                message.payloadText(),
                message.payloadBase64(),
                message.parseError()
        );
    }

    public HistoryPage findHistory(HistoryQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (StringUtils.hasText(query.interfaceName())) {
            where.append(" AND interface_name = ?");
            params.add(query.interfaceName());
        }
        if (StringUtils.hasText(query.messageType())) {
            where.append(" AND message_type = ?");
            params.add(query.messageType());
        }
        if (query.parseErrorOnly()) {
            where.append(" AND parse_error IS NOT NULL");
        }
        if (query.from() != null) {
            where.append(" AND observed_at >= ?");
            params.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND observed_at <= ?");
            params.add(Timestamp.from(query.to()));
        }

        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages" + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.limit());
        pageParams.add(query.offset());

        List<ObservedMessage> items = jdbcTemplate.query(
                "SELECT * FROM messages" + where + " ORDER BY observed_at DESC LIMIT ? OFFSET ?",
                rowMapper(),
                pageParams.toArray());

        return new HistoryPage(items, totalCount == null ? 0 : totalCount);
    }

    public List<TimeBucketCount> countByTimeBucket(Instant from, Instant to, TimeBucket bucket) {
        String field = switch (bucket) {
            case MINUTE -> "MINUTE";
            case HOUR -> "HOUR";
            case DAY -> "DAY";
        };

        String sql = "SELECT DATE_TRUNC(" + field + ", observed_at) AS bucket_start, COUNT(*) AS cnt "
                + "FROM messages WHERE observed_at >= ? AND observed_at <= ? "
                + "GROUP BY bucket_start ORDER BY bucket_start";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new TimeBucketCount(rs.getTimestamp("bucket_start").toInstant(), rs.getLong("cnt")),
                Timestamp.from(from), Timestamp.from(to));
    }

    public List<BreakdownCount> countByField(Instant from, Instant to, GroupByField field) {
        String column = field.columnName();
        String sql = "SELECT " + column + " AS field_key, COUNT(*) AS cnt FROM messages "
                + "WHERE observed_at >= ? AND observed_at <= ? "
                + "GROUP BY " + column + " ORDER BY cnt DESC";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new BreakdownCount(rs.getString("field_key"), rs.getLong("cnt")),
                Timestamp.from(from), Timestamp.from(to));
    }

    private RowMapper<ObservedMessage> rowMapper() {
        return (rs, rowNum) -> new ObservedMessage(
                rs.getString("id"),
                rs.getTimestamp("observed_at").toInstant(),
                rs.getString("transport_protocol"),
                rs.getString("remote_address"),
                rs.getInt("local_port"),
                rs.getString("interface_name"),
                rs.getString("message_type"),
                fromJson(rs.getString("header_json")),
                fromJson(rs.getString("body_json")),
                rs.getInt("payload_size_bytes"),
                rs.getString("payload_text"),
                rs.getString("payload_base64"),
                rs.getString("parse_error")
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize message field to JSON", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize message field from JSON", e);
        }
    }
}
