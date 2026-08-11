-- V11: Widen the embedding column from fixed vector(1536) to unbounded vector.
-- This allows non-OpenAI embedding providers (ZhiPuAI 1024d, Ollama 768d) to work.
-- The column was originally created in V3 with a hard-coded 1536 dimension.

DO $$
BEGIN
    ALTER TABLE memories ALTER COLUMN embedding TYPE vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Could not alter embedding column type — may already be unbounded or pgvector missing.';
END $$;
