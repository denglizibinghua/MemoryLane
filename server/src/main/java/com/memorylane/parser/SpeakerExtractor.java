package com.memorylane.parser;

import com.memorylane.adapter.model.RawMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw text into individual messages and extracts speaker, content and
 * raw time per message based on the detected platform format.
 *
 * <p>Output is a list of {@link RawMessage} where {@code rawTime} is already
 * parsed into an {@link Instant} (via {@link MessageNormalizer#parseTime}) when
 * the source contained a timestamp, otherwise {@code null}. Content hashing and
 * platform defaulting happen later in {@link MessageNormalizer#normalize}.
 *
 * <p>The self-speaker "我" is normalized to the reserved value {@value #SELF_SPEAKER}.
 */
@Component
public class SpeakerExtractor {

    /** Reserved speaker value for the user's own messages. */
    public static final String SELF_SPEAKER = "self";
    /** The Chinese self reference that maps to {@value #SELF_SPEAKER}. */
    private static final String SELF_NAME = "我";

    /** WeChat header with Chinese day-part time: {@code "张三 下午 2:30"}. */
    private static final Pattern WE_CHAT_DAY_PART_HEADER =
            Pattern.compile("^\\s*(.+?)\\s+(凌晨|早上|上午|中午|下午|晚上|夜里|半夜)\\s+(\\d{1,2}:\\d{2})\\s*$");
    /** WeChat header with slash date: {@code "张三 2024/3/15 14:30"}. */
    private static final Pattern WE_CHAT_DATE_HEADER =
            Pattern.compile("^\\s*(.+?)\\s+(\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*$");
    /** QQ header with dash date: {@code "张三 2024-03-15 14:30:00"}. */
    private static final Pattern QQ_HEADER =
            Pattern.compile("^\\s*(.+?)\\s+(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*$");
    /** Inline speaker separator for Douyin/generic: {@code "张三：周末去不去"}. */
    private static final Pattern INLINE_SEPARATOR =
            Pattern.compile("^\\s*(.+?)\\s*[：:]\\s*(.+?)\\s*$");

    /**
     * Extract structured messages from raw text for the given platform.
     *
     * @param rawText     the raw pasted text
     * @param platform    the detected platform (wechat | qq | douyin | sms | generic);
     *                    {@code null} or blank is treated as generic
     * @param contactHint optional contact name used as fallback speaker when a
     *                    message line carries no speaker; may be {@code null}
     * @return the extracted messages in original order; never {@code null}
     */
    public List<RawMessage> extract(String rawText, String platform, String contactHint) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String p = platform == null || platform.isBlank() ? PlatformDetector.GENERIC : platform.trim().toLowerCase();
        return switch (p) {
            case PlatformDetector.WECHAT -> extractWeChat(rawText);
            case PlatformDetector.QQ -> extractQq(rawText);
            case PlatformDetector.DOUYIN -> extractDouyin(rawText, contactHint);
            case PlatformDetector.SMS -> extractSms(rawText, contactHint);
            default -> extractGeneric(rawText, contactHint);
        };
    }

    /**
     * WeChat: header lines (speaker + space + time) start a message; following
     * non-blank lines until the next header form the content.
     */
    private List<RawMessage> extractWeChat(String rawText) {
        List<RawMessage> result = new ArrayList<>();
        String currentSpeaker = null;
        String currentTime = null;
        List<String> contentLines = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            Matcher dayPart = WE_CHAT_DAY_PART_HEADER.matcher(line);
            if (dayPart.matches()) {
                if (currentSpeaker != null) {
                    flush(result, currentSpeaker, currentTime, contentLines, PlatformDetector.WECHAT);
                }
                currentSpeaker = dayPart.group(1).trim();
                currentTime = dayPart.group(2) + " " + dayPart.group(3);
                contentLines = new ArrayList<>();
                continue;
            }
            Matcher date = WE_CHAT_DATE_HEADER.matcher(line);
            if (date.matches()) {
                if (currentSpeaker != null) {
                    flush(result, currentSpeaker, currentTime, contentLines, PlatformDetector.WECHAT);
                }
                currentSpeaker = date.group(1).trim();
                currentTime = date.group(2);
                contentLines = new ArrayList<>();
                continue;
            }
            if (currentSpeaker != null && !line.isBlank()) {
                contentLines.add(line.trim());
            }
        }
        if (currentSpeaker != null) {
            flush(result, currentSpeaker, currentTime, contentLines, PlatformDetector.WECHAT);
        }
        return result;
    }

    /**
     * QQ: same block structure as WeChat but headers carry dash-separated
     * ISO-ish timestamps.
     */
    private List<RawMessage> extractQq(String rawText) {
        List<RawMessage> result = new ArrayList<>();
        String currentSpeaker = null;
        String currentTime = null;
        List<String> contentLines = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            Matcher header = QQ_HEADER.matcher(line);
            if (header.matches()) {
                if (currentSpeaker != null) {
                    flush(result, currentSpeaker, currentTime, contentLines, PlatformDetector.QQ);
                }
                currentSpeaker = header.group(1).trim();
                currentTime = header.group(2);
                contentLines = new ArrayList<>();
                continue;
            }
            if (currentSpeaker != null && !line.isBlank()) {
                contentLines.add(line.trim());
            }
        }
        if (currentSpeaker != null) {
            flush(result, currentSpeaker, currentTime, contentLines, PlatformDetector.QQ);
        }
        return result;
    }

    /**
     * Douyin: one message per line using the inline {@code "speaker：content"}
     * separator. Lines without a separator keep their text as content and fall
     * back to the contact hint as speaker.
     */
    private List<RawMessage> extractDouyin(String rawText, String contactHint) {
        List<RawMessage> result = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher inline = INLINE_SEPARATOR.matcher(line);
            if (inline.matches()) {
                result.add(message(inline.group(1).trim(), inline.group(2).trim(), null, PlatformDetector.DOUYIN));
            } else {
                result.add(message(fallbackSpeaker(contactHint), line.trim(), null, PlatformDetector.DOUYIN));
            }
        }
        return result;
    }

    /**
     * SMS: consecutive non-blank lines form {@code "speaker\ncontent"} blocks.
     * A trailing unmatched line is treated as content with the contact hint as
     * fallback speaker.
     */
    private List<RawMessage> extractSms(String rawText, String contactHint) {
        List<RawMessage> result = new ArrayList<>();
        List<String> lines = nonBlankLines(rawText);
        for (int i = 0; i < lines.size(); i += 2) {
            String first = lines.get(i);
            if (i + 1 < lines.size()) {
                result.add(message(first, lines.get(i + 1), null, PlatformDetector.SMS));
            } else {
                result.add(message(fallbackSpeaker(contactHint), first, null, PlatformDetector.SMS));
            }
        }
        return result;
    }

    /**
     * Generic: blank lines separate messages. Each block tries the inline
     * {@code "speaker: content"} separator on its first line; otherwise the
     * whole block is content with the contact hint as fallback speaker.
     */
    private List<RawMessage> extractGeneric(String rawText, String contactHint) {
        List<RawMessage> result = new ArrayList<>();
        for (String block : rawText.split("\\n\\s*\\n")) {
            String b = block.trim();
            if (b.isEmpty()) {
                continue;
            }
            String[] blockLines = b.split("\\R");
            Matcher inline = INLINE_SEPARATOR.matcher(blockLines[0]);
            if (inline.matches()) {
                String rest = blockLines.length > 1
                        ? "\n" + String.join("\n", Arrays.copyOfRange(blockLines, 1, blockLines.length))
                        : "";
                result.add(message(inline.group(1).trim(), inline.group(2).trim() + rest, null, PlatformDetector.GENERIC));
            } else {
                result.add(message(fallbackSpeaker(contactHint), b, null, PlatformDetector.GENERIC));
            }
        }
        return result;
    }

    /** Finalize the current WeChat/QQ block into a message, skipping empty content. */
    private void flush(List<RawMessage> out, String speaker, String time, List<String> contentLines, String platform) {
        String content = String.join("\n", contentLines);
        if (content.isBlank()) {
            return;
        }
        out.add(message(speaker, content, MessageNormalizer.parseTime(time), platform));
    }

    private List<String> nonBlankLines(String rawText) {
        List<String> lines = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private RawMessage message(String speaker, String content, Instant rawTime, String platform) {
        return new RawMessage(toSpeaker(speaker), content, rawTime, platform, null);
    }

    /** Maps the Chinese self reference "我" to {@value #SELF_SPEAKER}. */
    private String toSpeaker(String speaker) {
        String s = speaker == null ? null : speaker.trim();
        return SELF_NAME.equals(s) ? SELF_SPEAKER : s;
    }

    private String fallbackSpeaker(String contactHint) {
        if (contactHint == null || contactHint.isBlank()) {
            return null;
        }
        return toSpeaker(contactHint);
    }
}
