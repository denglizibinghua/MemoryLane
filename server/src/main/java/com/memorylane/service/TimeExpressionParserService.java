package com.memorylane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylane.config.DelegatingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM-based time-expression parser for promise-category memories.
 *
 * <p>Extracts event time and generates a reminder timestamp from natural-language
 * expressions like "下周去爬山", "明天下午三点", "月底还钱". Uses a dedicated
 * {@link ChatClient} with its own system prompt, mirroring {@link AdvisorService}'s
 * constructor pattern to avoid polluting the extraction-pipeline prompts.
 */
@Slf4j
@Service
public class TimeExpressionParserService {

    private static final String SYSTEM_PROMPT = """
            你是一个时间解析助手。给定一条约定（promise）描述，提取其中的时间信息。

            要求：
            1. 识别约定文字中的时间表达（如"明天""下周""下个月""月底""周X""X月X日""X点"等）
            2. 解析出事件发生时间 eventTime，用 ISO 8601 格式（如 2026-08-16T09:00:00+08:00）
            3. 提醒时间 remindAt 默认比 eventTime 早 30 分钟，也用 ISO 8601 格式
            4. 提取一个简短的提醒标题 title（如"爬山""还钱""吃饭"），结合联系人姓名
            5. 提取原始文字中关于时间的描述片段 sourceText
            6. 如果约定里没有任何时间信息或无法解析，hasTime 设为 false，其余字段随意填充
            7. 只输出 JSON，不要任何解释文字。格式：
               {"hasTime":true,"memoryId":0,"title":"爬山","eventTime":"2026-08-16T09:00:00+08:00","remindAt":"...","sourceText":"下周去爬山"}
            """;

    private static final String USER_PROMPT_PREFIX = """
            待解析的约定记忆：
            记忆ID：""";
    private static final String USER_PROMPT_CONTACT = "\n联系人：";
    private static final String USER_PROMPT_CONTENT = "\n内容：";
    private static final String USER_PROMPT_SUFFIX = """

            当前时间：%s

            请提取时间信息，返回 JSON。
            """;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.of("Asia/Shanghai"));

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public TimeExpressionParserService(DelegatingChatModel delegatingChatModel,
                                       ChatClient.Builder builder,
                                       ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /**
     * Parse one promise memory for time information.
     *
     * @param memoryId    the memory's DB id (for LLM identification)
     * @param content     the memory's natural-language content
     * @param contactName the associated contact's name
     * @return parsed result; {@link ParsedTime#hasTime()} is false when LLM
     *         found no time expression or the call failed
     */
    public ParsedTime parse(long memoryId, String content, String contactName) {
        String safeContent = sanitizeContent(content);
        // Concatenation instead of String.format to avoid crash on '%' in content
        String userPrompt = USER_PROMPT_PREFIX + memoryId
                + USER_PROMPT_CONTACT + contactName
                + USER_PROMPT_CONTENT + safeContent
                + String.format(USER_PROMPT_SUFFIX, ISO_FORMATTER.format(Instant.now()));

        log.info("Time parse for memory={}, contact={}", memoryId, contactName);
        try {
            String response = chatClient.prompt().user(u -> u.text(userPrompt)).call().content();
            log.info("Time parse response: {}", response);
            return parseResponse(response, memoryId);
        } catch (Exception e) {
            log.warn("Time parse failed for memory={}", memoryId, e);
            return new ParsedTime(memoryId, false, null, null, null, null);
        }
    }

    /**
     * Parse a single response into the record.
     */
    ParsedTime parseResponse(String response, long memoryId) {
        try {
            Map<String, Object> root = objectMapper.readValue(
                    extractJson(response), new TypeReference<HashMap<String, Object>>() {});
            Object hasTimeRaw = root.get("hasTime");
            boolean hasTime = hasTimeRaw instanceof Boolean b ? b
                    : "true".equalsIgnoreCase(String.valueOf(hasTimeRaw));
            if (!hasTime) {
                return new ParsedTime(memoryId, false, null, null, null, null);
            }
            String title = toStringOrEmpty(root.get("title"));
            String sourceText = toStringOrEmpty(root.get("sourceText"));
            Instant eventTime = parseInstant(root.get("eventTime"));
            Instant remindAt = parseInstant(root.get("remindAt"));
            return new ParsedTime(memoryId, true, title, eventTime, remindAt, sourceText);
        } catch (Exception e) {
            log.warn("Failed to parse time JSON: {}", response, e);
            return new ParsedTime(memoryId, false, null, null, null, null);
        }
    }

    /**
     * Trim the LLM response to the JSON object/array portion.
     * @implNote Copied from {@code AdvisorService.extractJson}.
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        String s = response.trim();
        int braceStart = s.indexOf('{');
        if (braceStart >= 0) {
            int braceEnd = s.lastIndexOf('}');
            if (braceEnd > braceStart) return s.substring(braceStart, braceEnd + 1);
        }
        int arrayStart = s.indexOf('[');
        int arrayEnd = s.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) return s.substring(arrayStart, arrayEnd + 1);
        return "{}";
    }

    private Instant parseInstant(Object raw) {
        if (raw == null) return null;
        try {
            return Instant.from(ISO_FORMATTER.parse(String.valueOf(raw)));
        } catch (Exception e) {
            log.debug("Cannot parse instant: {}", raw);
            return null;
        }
    }

    private String toStringOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String sanitizeContent(String content) {
        if (content == null) return "";
        return content.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /**
     * Result of time-expression parsing for a single promise memory.
     */
    public record ParsedTime(
            long memoryId,
            boolean hasTime,
            String title,
            Instant eventTime,
            Instant remindAt,
            String sourceText
    ) {}
}
