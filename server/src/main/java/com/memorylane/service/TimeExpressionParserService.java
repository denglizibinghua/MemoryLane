package com.memorylane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * expressions like "下周去爬山", "明天下午三点", "月底还钱".
 *
 * <p>系统 & 用户提示词来自 {@link PromptTemplateService}（key={@code time_parser.system}
 * 和 {@code time_parser.user}）。模板修改即时生效，无需重启。
 */
@Slf4j
@Service
public class TimeExpressionParserService {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.of("Asia/Shanghai"));

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public TimeExpressionParserService(ChatClient.Builder builder,
                                       PromptTemplateService promptTemplateService,
                                       ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
        this.chatClient = builder.build();
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
        String systemPrompt = promptTemplateService.getTemplate("time_parser.system");
        String userTemplate = promptTemplateService.getTemplate("time_parser.user");
        String userPrompt = userTemplate
                .replace("{memoryId}", String.valueOf(memoryId))
                .replace("{contactName}", contactName)
                .replace("{content}", safeContent)
                .replace("{now}", ISO_FORMATTER.format(Instant.now()));

        log.info("Time parse for memory={}, contact={}", memoryId, contactName);
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt))
                    .call().content();
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
