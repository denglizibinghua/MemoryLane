-- V5: 修复 CJK 字符被当作单个 token 导致全文搜索失效
--
-- 根因: to_tsvector('simple', '发烧') → '发烧':1 (整个词一个 token)
--       to_tsquery('simple', '发烧') → '发烧' (一个查询 token)
--       'lim发烧刚好' @@ '发烧' → false (token 不相等)
--
-- 修复: 在 tsvector 构建前，用 regexp_replace 在 CJK 字符两侧插入空格，
--       让每个汉字成为独立 token。
--       例: 'lim发烧刚好' → 'lim 发 烧 刚 好 ' → tokens: lim, 发, 烧, 刚, 好

-- 1. 更新触发器函数
CREATE OR REPLACE FUNCTION memories_fts_trigger() RETURNS trigger AS $$
BEGIN
    NEW.fts := setweight(
        to_tsvector('simple', regexp_replace(
            coalesce(NEW.content, ''),
            '[\u4E00-\u9FFF]', ' \& ', 'g'
        )),
        'A'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 重建已有数据的 fts 列
UPDATE memories
SET fts = setweight(
    to_tsvector('simple', regexp_replace(
        coalesce(content, ''),
        '[\u4E00-\u9FFF]', ' \& ', 'g'
    )),
    'A'
);
