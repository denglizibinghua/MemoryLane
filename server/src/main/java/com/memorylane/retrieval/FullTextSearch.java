package com.memorylane.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PostgreSQL tsvector 全文搜索。
 *
 * <p>使用 {@code to_tsquery('simple', query)} 将用户输入转为 tsquery，
 * 通过 {@code ts_rank(fts, query, 1)} 计算排名，限制 20 条结果。
 */
@Component
@RequiredArgsConstructor
public class FullTextSearch {

    private final JdbcTemplate jdbc;

    /**
     * 全文检索记忆。
     *
     * @param query     用户搜索词
     * @param contactId 联系人 ID（null = 全局搜索）
     * @param limit     最大返回数
     */
    public List<SearchResult> search(String query, Long contactId, int limit) {
        String tsQuery = toTsQuery(query);
        if (tsQuery.isEmpty()) return List.of();

        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.content, m.category::text, m.confidence,
                       m.contact_id, c.name AS contact_name,
                       ts_rank(m.fts, to_tsquery('simple', ?), 1) AS score
                FROM memories m
                JOIN contacts c ON c.id = m.contact_id
                WHERE m.fts @@ to_tsquery('simple', ?)
                  AND m.valid_until IS NULL
                """);

        if (contactId != null) {
            sql.append(" AND m.contact_id = ?");
        }
        sql.append(" ORDER BY score DESC LIMIT ?");

        Object[] params;
        if (contactId != null) {
            params = new Object[]{tsQuery, tsQuery, contactId, limit};
        } else {
            params = new Object[]{tsQuery, tsQuery, limit};
        }

        return jdbc.query(sql.toString(), ps -> {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }, (rs, rowNum) -> new SearchResult(
                "memory",
                rs.getString("content"),
                rs.getDouble("score"),
                rs.getString("category"),
                rs.getLong("id"),
                rs.getLong("contact_id"),
                rs.getString("contact_name")
        ));
    }

    /**
     * "发烧" → "发 & 烧"
     * "lim发烧" → "lim & 发 & 烧"
     */
    static String toTsQuery(String query) {
        if (query == null || query.isBlank()) return "";
        // 在 CJK 字符两侧插入空格，让每个汉字成为独立查询 token
        String cjkSplit = query.replaceAll("([\\u4E00-\\u9FFF])", " $1 ");
        String cleaned = cjkSplit.replaceAll("[^\\u4E00-\\u9FFFa-zA-Z0-9]", " ");
        String[] words = cleaned.trim().split("\\s+");
        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) return "";
        return String.join(" & ", words);
    }
}
