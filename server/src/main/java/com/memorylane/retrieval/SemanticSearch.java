package com.memorylane.retrieval;

import com.memorylane.config.DelegatingEmbeddingModel;
import com.memorylane.entity.Memory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pgvector 语义搜索 + embedding 生成。
 *
 * <p>使用 {@link DelegatingEmbeddingModel} — 当用户未在设置页开启语义搜索时，
 * embed() 返回 null，search() 返回空列表，全文搜索降级运行。
 */
@Slf4j
@Component
public class SemanticSearch {

    private final DelegatingEmbeddingModel embeddingModel;
    private final JdbcTemplate jdbc;

    public SemanticSearch(DelegatingEmbeddingModel embeddingModel, JdbcTemplate jdbc) {
        this.embeddingModel = embeddingModel;
        this.jdbc = jdbc;
    }

    /**
     * 语义检索记忆。
     */
    public List<SearchResult> search(String query, Long contactId, int limit) {
        float[] vec = embed(query);
        if (vec == null) return List.of();

        String vectorStr = toVectorLiteral(vec);

        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.content, m.category::text, m.confidence,
                       m.contact_id, c.name AS contact_name,
                       1.0 - (m.embedding <=> %s::vector) AS score
                FROM memories m
                JOIN contacts c ON c.id = m.contact_id
                WHERE m.embedding IS NOT NULL
                  AND m.valid_until IS NULL
                """.formatted(vectorStr));

        if (contactId != null) {
            sql.append(" AND m.contact_id = ").append(contactId);
        }
        sql.append(" ORDER BY score DESC LIMIT ").append(limit);

        try {
            return jdbc.query(sql.toString(), (rs, rowNum) -> new SearchResult(
                "memory",
                rs.getString("content"),
                rs.getDouble("score"),
                rs.getString("category"),
                rs.getLong("id"),
                rs.getLong("contact_id"),
                rs.getString("contact_name")
        ));
    } catch (Exception e) {
        log.warn("Semantic search unavailable (pgvector not installed): {}", e.getMessage());
        return List.of();
    }
    }

    private float[] embed(String text) {
        if (embeddingModel == null) {
            log.debug("Embedding model not configured — skipping semantic embedding");
            return null;
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Embedding failed: {}", text, e);
            return null;
        }
    }

    static String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("'[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append("]'");
        return sb.toString();
    }

    /**
     * 为指定记忆生成并存储 embedding。
     */
    public void generateAndStore(Memory memory) {
        float[] vec = embed(memory.getContent());
        if (vec == null) return;
        String literal = toVectorLiteral(vec);
        jdbc.update("UPDATE memories SET embedding = " + literal + "::vector WHERE id = " + memory.getId());
    }
}
