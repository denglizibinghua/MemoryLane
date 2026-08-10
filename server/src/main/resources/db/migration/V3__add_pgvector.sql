-- ============================================================
-- MemoryLane V3 — pgvector extension + embedding column
-- Gracefully skipped if pgvector is not installed (bare PostgreSQL)
-- ============================================================

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
    ALTER TABLE memories ADD COLUMN IF NOT EXISTS embedding vector(1536);
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector not available — semantic search disabled. Install pgvector or use Docker Compose.';
END $$;
