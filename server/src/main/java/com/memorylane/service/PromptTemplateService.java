package com.memorylane.service;

import com.memorylane.entity.PromptTemplate;
import com.memorylane.repository.PromptTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt template service — in-memory cache + DB-backed persistence.
 *
 * <p>On startup, loads all templates from DB into a {@link ConcurrentHashMap}.
 * If the DB is empty (first run, migration not yet applied), falls back to
 * {@link #BUILTIN_DEFAULTS} — the same values V10 seeds.
 *
 * <p>Consumers call {@link #getTemplate(String)} at LLM invocation time, so
 * template edits take effect immediately (no restart required).
 */
@Slf4j
@Service
public class PromptTemplateService {

    private final PromptTemplateRepository repository;

    /** Volatile cache — replaced atomically on refresh. */
    private volatile Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Compile-time fallback (must match V10 seed values). Used when the DB
     * migration hasn't run yet or all rows were deleted.
     */
    static final Map<String, String> BUILTIN_DEFAULTS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("pipeline.system",
                "你是一个聊天记录分析助手，负责从对话中提取结构化信息。只输出要求的 JSON 格式，不要额外解释。");
        m.put("importance.user",
                "分析以下聊天消息，按重要性分为三类：\n\n"
                + "1 (日常寒暄) — 打招呼、表情包、无信息量的回应\n"
                + "2 (有价值) — 包含具体信息、计划、偏好、事件\n"
                + "3 (关键记忆) — 重要约定、个人信息变更、感情表达、长期承诺\n\n"
                + "输出 JSON 数组，只输出有重要性的消息（2 或 3）：\n"
                + "[{\"id\": 消息编号, \"importance\": 2}, ...]\n\n"
                + "消息列表：\n"
                + "{messages}");
        m.put("fact_extractor.user",
                "从以下聊天记录中提取结构化事实，每条事实须归类为以下之一：\n\n"
                + "- promise：约定、承诺、计划（如\"下周去爬山\"\"下个月见面\"）\n"
                + "- personal_info：个人具体信息（如\"我在字节实习\"\"我住在朝阳区\"\"手机号138\"）\n"
                + "- preference：偏好、喜好、厌恶（如\"不吃香菜\"\"喜欢看悬疑片\"\"讨厌下雨天\"）\n"
                + "- event：已发生的具体事件（如\"昨天面试了\"\"上周去看了演唱会\"\"上次吵架是三月\"）\n"
                + "- persona：性格特征、说话习惯、行为模式（如\"回复很快\"\"喜欢用～\"\"经常深夜聊天\"）\n"
                + "- relationship：关系动态和评价（如\"最近聊天变少了\"\"你俩很默契\"\"他对你一直很体贴\"）\n\n"
                + "规则：\n"
                + "1. 每条事实的内容必须是完整的中文句子，能脱离上下文独立理解\n"
                + "2. 已明确表达的事实 confidence = 0.85-0.95；可推断的 confidence = 0.5-0.7\n"
                + "3. 不要提取日常寒暄、表情包、拉家常\n"
                + "4. 同一个人（同一个说话人）说过多次类似内容，取最新的一条，提高 confidence\n\n"
                + "只输出一个 JSON 数组：\n"
                + "[{\"category\":\"preference\",\"content\":\"喜欢吃川菜，尤其火锅\",\"confidence\":0.85}]\n\n"
                + "聊天记录：\n"
                + "{messages}");
        m.put("advisor.system",
                "你是\"{userName}\"的回复助手。你的任务是根据关于对方的历史记忆和当前对话上下文，生成自然、得体的回复建议。\n\n"
                + "要求：\n"
                + "1. 生成 2-3 条回复建议，每条包含 style（风格标签，如\"轻松随性\"\"引用约定\"\"关心体贴\"）、content（回复文字，自然口语）、reason（为何这样回复，引用哪条记忆）\n"
                + "2. 生成 1-2 条新话题建议（不回复对方时可以聊什么），每条包含 content（话题内容）、reason（为什么）\n"
                + "3. 回复风格必须参考对方特征和你们的历史互动\n"
                + "4. 只输出 JSON，格式: {\"replies\":[{\"style\":\"...\",\"content\":\"...\",\"reason\":\"...\"}],\"newTopics\":[{\"content\":\"...\",\"reason\":\"...\"}]}");
        m.put("advisor.user",
                "对方姓名：{contactName}\n"
                + "{profileSection}\n"
                + "当前对话上下文：\n"
                + "{context}\n\n"
                + "对方最近一条消息：{lastMessage}\n\n"
                + "相关历史记忆：\n"
                + "{memories}");
        m.put("time_parser.system",
                "你是一个时间解析助手。给定一条约定（promise）描述，提取其中的时间信息。\n\n"
                + "要求：\n"
                + "1. 识别约定文字中的时间表达（如\"明天\"\"下周\"\"下个月\"\"月底\"\"周X\"\"X月X日\"\"X点\"等）\n"
                + "2. 解析出事件发生时间 eventTime，用 ISO 8601 格式（如 2026-08-16T09:00:00+08:00）\n"
                + "3. 提醒时间 remindAt 默认比 eventTime 早 30 分钟，也用 ISO 8601 格式\n"
                + "4. 提取一个简短的提醒标题 title（如\"爬山\"\"还钱\"\"吃饭\"），结合联系人姓名\n"
                + "5. 提取原始文字中关于时间的描述片段 sourceText\n"
                + "6. 如果约定里没有任何时间信息或无法解析，hasTime 设为 false，其余字段随意填充\n"
                + "7. 只输出 JSON，不要任何解释文字。格式：\n"
                + "   {\"hasTime\":true,\"memoryId\":0,\"title\":\"爬山\",\"eventTime\":\"2026-08-16T09:00:00+08:00\",\"remindAt\":\"...\",\"sourceText\":\"下周去爬山\"}");
        m.put("time_parser.user",
                "待解析的约定记忆：\n"
                + "记忆ID：{memoryId}\n"
                + "联系人：{contactName}\n"
                + "内容：{content}\n\n"
                + "当前时间：{now}\n\n"
                + "请提取时间信息，返回 JSON。");
        m.put("profile.analyze.system",
                "分析以下聊天记录中\"我\"（self）的说话风格、性格特征、身份信息。\n\n"
                + "要求：\n"
                + "1. persona: 一句话描述身份和性格（如\"22岁男大学生，青岛上学，计算机专业，性格直接幽默\"）\n"
                + "2. speakingStyle: 说话风格标签（如\"幽默/直接/温柔/土味/优雅/理性\"）\n"
                + "3. relationship: 和大多数聊天对象的关系类型（如\"同学/朋友/暧昧/同事\"）\n\n"
                + "只输出JSON: {\"persona\":\"...\",\"speakingStyle\":\"...\",\"relationship\":\"...\"}");
        m.put("profile.analyze.user",
                "我的聊天记录：\n"
                + "{sample}");
        m.put("ocr.screenshot.user",
                "请识别这张聊天记录截图中的所有文字。按对话格式逐条输出，每条消息一行。\n"
                + "如果能看到时间戳，保留原始时间格式。\n"
                + "格式示例：\n"
                + "张三: 2024-01-15 14:30:00 你在干嘛\n"
                + "李四: 2024-01-15 14:31:00 刚吃完饭\n\n"
                + "注意：\n"
                + "- 不要添加额外解释，只输出对话内容\n"
                + "- 保持说话人和内容的对应关系\n"
                + "- 如果截图中有群聊名称，请在开头用「群聊：名称」标注");
        BUILTIN_DEFAULTS = Collections.unmodifiableMap(m);
    }

    public PromptTemplateService(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void init() {
        refresh();
        log.info("PromptTemplateService initialized: {} templates loaded", cache.size());
    }

    /**
     * Get a template by key. Falls back to built-in default if not in cache.
     */
    public String getTemplate(String key) {
        String cached = cache.get(key);
        if (cached != null) return cached;
        String fallback = BUILTIN_DEFAULTS.get(key);
        if (fallback != null) {
            log.debug("Template '{}' not in cache, using built-in default", key);
        }
        return fallback != null ? fallback : "";
    }

    /**
     * Return all templates as a map of key → content (for the settings UI).
     */
    public Map<String, String> getAllTemplates() {
        Map<String, String> result = new LinkedHashMap<>();
        // Iterate BUILTIN_DEFAULTS keys in order, pulling from cache or fallback
        for (String key : BUILTIN_DEFAULTS.keySet()) {
            result.put(key, cache.getOrDefault(key, BUILTIN_DEFAULTS.get(key)));
        }
        return result;
    }

    /**
     * Return template metadata (key, name, description) for the settings UI.
     */
    public Map<String, Map<String, Object>> getAllTemplateMeta() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String key : BUILTIN_DEFAULTS.keySet()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            // Try to get name/description from DB, fall back to default
            PromptTemplate entity = repository.findById(key).orElse(null);
            meta.put("key", key);
            meta.put("name", entity != null ? entity.getName() : key);
            meta.put("description", entity != null ? entity.getDescription() : "");
            meta.put("isBuiltin", entity != null ? entity.isBuiltin() : true);
            meta.put("content", cache.getOrDefault(key, BUILTIN_DEFAULTS.get(key)));
            result.put(key, meta);
        }
        return result;
    }

    /**
     * Batch-update templates. Only keys present in {@code updates} are changed.
     * Keys not in BUILTIN_DEFAULTS are silently ignored.
     */
    @Transactional
    public void updateTemplates(Map<String, String> updates) {
        for (Map.Entry<String, String> e : updates.entrySet()) {
            String key = e.getKey();
            String content = e.getValue();
            if (!BUILTIN_DEFAULTS.containsKey(key)) {
                log.warn("Ignoring unknown template key: {}", key);
                continue;
            }
            PromptTemplate pt = repository.findById(key).orElse(null);
            if (pt == null) {
                // Create if somehow missing (shouldn't happen after V10)
                pt = new PromptTemplate();
                pt.setKey(key);
                pt.setName(key);
                pt.setBuiltin(true);
                pt.setCreatedAt(LocalDateTime.now());
            }
            pt.setContent(content);
            pt.setUpdatedAt(LocalDateTime.now());
            repository.save(pt);
        }
        refresh();
    }

    /**
     * Reload all templates from DB, falling back to built-in defaults for missing keys.
     */
    public void refresh() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        for (PromptTemplate pt : repository.findAll()) {
            fresh.put(pt.getKey(), pt.getContent());
        }
        // Fill in any built-in keys missing from DB
        for (Map.Entry<String, String> e : BUILTIN_DEFAULTS.entrySet()) {
            fresh.putIfAbsent(e.getKey(), e.getValue());
        }
        this.cache = fresh;
        log.debug("Prompt template cache refreshed: {} entries", cache.size());
    }
}
