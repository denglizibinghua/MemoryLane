package com.memorylane.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Detects the chat platform format from raw pasted text using regex signatures.
 *
 * <p>Supported platforms, in detection priority order (first match wins on ties):
 * <ul>
 *   <li>{@value #WECHAT} — header lines like {@code "张三 下午 2:30"} or {@code "张三 2024/3/15 14:30"}</li>
 *   <li>{@value #QQ} — header lines like {@code "张三 2024-03-15 14:30:00"}</li>
 *   <li>{@value #DOUYIN} — lines like {@code "张三：周末去不去"}</li>
 *   <li>{@value #SMS} — two-line blocks {@code "张三\n周末去不去"}</li>
 *   <li>{@value #GENERIC} — fallback when nothing above matches</li>
 * </ul>
 *
 * <p>Detection counts how many lines in the raw text match each signature and
 * picks the platform with the highest count. Ties resolve to the platform
 * listed earlier above, since the SMS heuristic is intentionally the weakest.
 */
@Component
public class PlatformDetector {

    /** WeChat chat export format. */
    public static final String WECHAT = "wechat";
    /** QQ chat export format. */
    public static final String QQ = "qq";
    /** Douyin chat export format. */
    public static final String DOUYIN = "douyin";
    /** SMS message format. */
    public static final String SMS = "sms";
    /** Generic fallback format. */
    public static final String GENERIC = "generic";

    /** WeChat header with Chinese day-part time: {@code "张三 下午 2:30"}. */
    private static final Pattern WE_CHAT_DAY_PART =
            Pattern.compile("^\\s*.+?\\s+(凌晨|早上|上午|中午|下午|晚上|夜里|半夜)\\s+\\d{1,2}:\\d{2}\\s*$");
    /** WeChat header with slash date: {@code "张三 2024/3/15 14:30"} (slash-separated date). */
    private static final Pattern WE_CHAT_DATE =
            Pattern.compile("^\\s*.+?\\s+\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$");
    /** QQ header: {@code "张三 2024-03-15 14:30:00"} (dash-separated date). */
    private static final Pattern QQ_TIME =
            Pattern.compile("^\\s*.+?\\s+\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$");
    /** WeChat PC copy-paste format: {@code "张三 14:30"} (simple name + time, no date). */
    private static final Pattern WE_CHAT_SIMPLE_TIME =
            Pattern.compile("^\\s*.+?\\s+\\d{1,2}:\\d{2}\\s*$");
    /** WeChat PC copy-paste with Chinese date: {@code "2026年08月10日 17:55"}. */
    static final Pattern WE_CHAT_CN_DATE =
            Pattern.compile("^\\s*\\d{4}年\\d{1,2}月\\d{1,2}日\\s+\\d{1,2}:\\d{2}\\s*$");
    /** Douyin line: {@code "张三：周末去不去"} (full- or half-width colon separator). */
    private static final Pattern DOUYIN_INLINE =
            Pattern.compile("^\\s*.+?[：:]\\s*\\S+\\s*$");
    /** Sentence punctuation that disqualifies a line from being an SMS sender name. */
    private static final Pattern SENTENCE_PUNCTUATION =
            Pattern.compile("[，。？！；、,.?!;:：]");
    /** Digits disqualify a line from being an SMS sender name (names rarely contain them). */
    private static final Pattern HAS_DIGITS = Pattern.compile("\\d");

    /**
     * Detect the platform format of the given raw text.
     *
     * @param rawText the raw pasted text, may be {@code null} or blank
     * @return one of {@value #WECHAT}, {@value #QQ}, {@value #DOUYIN}, {@value #SMS}
     *         or {@value #GENERIC} when no signature matches
     */
    public String detect(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return GENERIC;
        }
        String best = GENERIC;
        int bestCount = 0;
        // Priority order: earlier platforms win ties.
        for (String platform : new String[]{WECHAT, QQ, DOUYIN, SMS}) {
            int count = countFor(platform, rawText);
            if (count > bestCount) {
                bestCount = count;
                best = platform;
            }
        }
        return best;
    }

    private int countFor(String platform, String rawText) {
        return switch (platform) {
            case WECHAT -> countLines(rawText, WE_CHAT_DAY_PART)
                    + countLines(rawText, WE_CHAT_DATE)
                    + countLines(rawText, WE_CHAT_SIMPLE_TIME)
                    + countLines(rawText, WE_CHAT_CN_DATE);
            case QQ -> countLines(rawText, QQ_TIME);
            case DOUYIN -> countLines(rawText, DOUYIN_INLINE);
            case SMS -> countSmsBlocks(rawText);
            default -> 0;
        };
    }

    private int countLines(String rawText, Pattern pattern) {
        int count = 0;
        for (String line : rawText.split("\\R")) {
            if (pattern.matcher(line).matches()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts candidate two-line SMS blocks: a sender-like first line followed by
     * a non-blank content line.
     */
    private int countSmsBlocks(String rawText) {
        String[] lines = rawText.split("\\R");
        int count = 0;
        for (int i = 0; i + 1 < lines.length; i++) {
            if (looksLikeSmsSender(lines[i]) && !lines[i + 1].isBlank()) {
                count++;
            }
        }
        return count;
    }

    /**
     * A plausible SMS sender line: short, punctuation-free, digit-free, and not
     * matching any other platform's header signature.
     */
    private boolean looksLikeSmsSender(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.length() > 20) {
            return false;
        }
        if (SENTENCE_PUNCTUATION.matcher(t).find() || HAS_DIGITS.matcher(t).find()) {
            return false;
        }
        return !WE_CHAT_DAY_PART.matcher(t).matches()
                && !WE_CHAT_DATE.matcher(t).matches()
                && !WE_CHAT_SIMPLE_TIME.matcher(t).matches()
                && !WE_CHAT_CN_DATE.matcher(t).matches()
                && !QQ_TIME.matcher(t).matches()
                && !DOUYIN_INLINE.matcher(t).matches();
    }
}
