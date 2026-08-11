package com.memorylane.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Relationship dynamics analytics — message trends and per-contact stats.
 * <p>
 * Uses native SQL with {@code date_trunc} for time-bucket aggregation
 * across the messages → conversations → contacts join path.
 */
@Slf4j
@Service
public class AnalyticsService {

    private final EntityManager em;

    public AnalyticsService(EntityManager em) {
        this.em = em;
    }

    /** Single data point on a trend line. */
    public record TrendPoint(String bucket, long msgCount, long selfCount) {}

    /** Per-contact high-level stats. */
    public record ContactStats(long contactId, String contactName,
                               long totalMessages, long selfCount,
                               double selfRatio, Instant firstMsgAt, Instant lastMsgAt) {}

    @Transactional(readOnly = true)
    public List<TrendPoint> getTrends(long contactId, String granularity) {
        String trunc = "week".equalsIgnoreCase(granularity) ? "week" : "month";
        // date_trunc returns timestamp; cast to date so the frontend gets a clean label
        String sql = """
            SELECT date_trunc('%s', COALESCE(m.raw_time, m.created_at))::date AS bucket,
                   COUNT(*)                                  AS msg_count,
                   COUNT(CASE WHEN m.speaker = 'self' THEN 1 END) AS self_count
            FROM messages m
            JOIN conversations c ON m.conversation_id = c.id
            WHERE c.contact_id = :contactId
            GROUP BY bucket
            ORDER BY bucket
            """.formatted(trunc);

        Query q = em.createNativeQuery(sql);
        q.setParameter("contactId", contactId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<TrendPoint> points = new ArrayList<>();
        for (Object[] row : rows) {
            String bucket = row[0].toString();             // date column → "2026-08-11"
            long msgCount = ((Number) row[1]).longValue();
            long selfCount = ((Number) row[2]).longValue();
            points.add(new TrendPoint(bucket, msgCount, selfCount));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<ContactStats> getOverview() {
        String sql = """
            SELECT ct.id                               AS contact_id,
                   ct.name                             AS contact_name,
                   COUNT(*)                            AS total_messages,
                   COUNT(CASE WHEN m.speaker = 'self' THEN 1 END) AS self_count,
                   MIN(m.raw_time)                     AS first_msg_at,
                   MAX(m.raw_time)                     AS last_msg_at
            FROM messages m
            JOIN conversations c ON m.conversation_id = c.id
            JOIN contacts ct ON c.contact_id = ct.id
            GROUP BY ct.id, ct.name
            ORDER BY total_messages DESC
            """;

        Query q = em.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<ContactStats> stats = new ArrayList<>();
        for (Object[] row : rows) {
            long contactId = ((Number) row[0]).longValue();
            String contactName = (String) row[1];
            long totalMessages = ((Number) row[2]).longValue();
            long selfCount = ((Number) row[3]).longValue();
            Instant firstMsgAt = toInstant(row[4]);
            Instant lastMsgAt = toInstant(row[5]);
            double selfRatio = totalMessages > 0 ? (double) selfCount / totalMessages : 0;
            stats.add(new ContactStats(contactId, contactName, totalMessages, selfCount,
                    selfRatio, firstMsgAt, lastMsgAt));
        }
        return stats;
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant i) return i;
        if (obj instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (obj instanceof java.util.Date d) return d.toInstant();
        return null;
    }
}
