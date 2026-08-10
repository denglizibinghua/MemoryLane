package com.memorylane.parser;

import com.memorylane.adapter.model.RawMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a list of extracted {@link RawMessage}s: assigns a content hash for
 * deduplication, guarantees a non-empty platform, and owns the time-string
 * parsing used throughout the pipeline.
 *
 * <p>Time strings handled by {@link #parseTime}: ISO 8601 (with zone), local
 * date-times ({@code 2024-03-15 14:30:00}, {@code 2024/3/15 14:30}),
 * Chinese day-part expressions ({@code 下午 2:30}, {@code 上午 10:00},
 * {@code 晚上 8:00}, {@code 凌晨 3:00}), relative day references
 * ({@code 昨天 下午 3:00}), bare times, and relative offsets ({@code 5分钟前}).
 * Unknown or unparseable times yield {@code null} — callers must insert null-safe.
 */
@Component
public class MessageNormalizer {

    /** Default zone for date-less time expressions (today's date is assumed). */
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    /** Chinese day-part time: {@code "下午 2:30"}. */
    private static final Pattern DAY_PART_TIME =
            Pattern.compile("^\\s*(凌晨|早上|上午|中午|下午|晚上|夜里|半夜)\\s+(\\d{1,2}):(\\d{2})\\s*$");
    /** Date + day-part + time: {@code "2024/3/15 下午 2:30"}. */
    private static final Pattern DATE_DAY_PART_TIME =
            Pattern.compile("^\\s*(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\s+(凌晨|早上|上午|中午|下午|晚上|夜里|半夜)\\s+(\\d{1,2}):(\\d{2})\\s*$");
    /** Relative day + optional day-part + time: {@code "昨天 下午 3:00"}. */
    private static final Pattern RELATIVE_DAY_TIME =
            Pattern.compile("^\\s*(今天|明天|昨天|前天|大前天)\\s+(凌晨|早上|上午|中午|下午|晚上|夜里|半夜)?\\s*(\\d{1,2}):(\\d{2})\\s*$");
    /** Bare time: {@code "14:30"}. */
    private static final Pattern BARE_TIME =
            Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*$");
    /** Relative offset: {@code "5分钟前"}, {@code "3小时前"}. */
    private static final Pattern AGO =
            Pattern.compile("^\\s*(\\d+)\\s*(秒|分钟|小时|天)前\\s*$");
    /** "Now" expressions. */
    private static final Pattern NOW =
            Pattern.compile("^\\s*(刚刚|刚才|现在)\\s*$");

    private static final DateTimeFormatter DATE_DASH = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter DATE_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DT_SECONDS_DASH = DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss");
    private static final DateTimeFormatter DT_MINUTES_DASH = DateTimeFormatter.ofPattern("yyyy-M-d H:mm");
    private static final DateTimeFormatter DT_SECONDS_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss");
    private static final DateTimeFormatter DT_MINUTES_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private static final DateTimeFormatter DT_SECONDS_T = DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm:ss");
    private static final DateTimeFormatter DT_MINUTES_T = DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm");

    /**
     * Normalize a list of extracted messages.
     *
     * @param messages the extracted messages; may be {@code null} or empty
     * @param platform the platform to stamp on every message; {@code null} or
     *                 blank is replaced with {@value PlatformDetector#GENERIC}
     * @return normalized messages; never {@code null}
     */
    public List<RawMessage> normalize(List<RawMessage> messages, String platform) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        String p = platform == null || platform.isBlank() ? PlatformDetector.GENERIC : platform.trim().toLowerCase();
        List<RawMessage> result = new ArrayList<>(messages.size());
        for (RawMessage message : messages) {
            if (message == null) {
                continue;
            }
            String speaker = message.speaker() == null ? "" : message.speaker();
            String content = message.content() == null ? "" : message.content();
            String hash = sha256(speaker + "|" + content);
            result.add(new RawMessage(speaker, content, message.rawTime(), p, hash));
        }
        return result;
    }

    /**
     * SHA-256 content hash used for deduplication.
     *
     * @param input the text to hash (never {@code null})
     * @return lowercase 64-char hex digest
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Parse a chat time expression into an {@link Instant}.
     *
     * @param raw the raw time string, may be {@code null} or blank
     * @return the parsed instant, or {@code null} when unparseable
     */
    public static Instant parseTime(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }

        // ISO 8601 with explicit zone: "2024-03-15T14:30:00Z"
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // fall through to the local formats below
        }

        // "刚刚" / "刚才" / "现在"
        Matcher now = NOW.matcher(s);
        if (now.matches()) {
            return Instant.now();
        }

        // "5分钟前" / "3小时前" / "2天前"
        Matcher ago = AGO.matcher(s);
        if (ago.matches()) {
            long n = Long.parseLong(ago.group(1));
            Duration duration = switch (ago.group(2)) {
                case "秒" -> Duration.ofSeconds(n);
                case "分钟" -> Duration.ofMinutes(n);
                case "小时" -> Duration.ofHours(n);
                case "天" -> Duration.ofDays(n);
                default -> null;
            };
            if (duration != null) {
                return Instant.now().minus(duration);
            }
        }

        // "2024/3/15 下午 2:30"
        Matcher dateDayPart = DATE_DAY_PART_TIME.matcher(s);
        if (dateDayPart.matches()) {
            LocalDate date = parseDate(dateDayPart.group(1));
            if (date != null) {
                int hour = dayPartHour(dateDayPart.group(2), Integer.parseInt(dateDayPart.group(3)));
                return at(date, hour, Integer.parseInt(dateDayPart.group(4)));
            }
        }

        // "昨天 下午 3:00", "明天 10:00"
        Matcher relativeDay = RELATIVE_DAY_TIME.matcher(s);
        if (relativeDay.matches()) {
            int offset = switch (relativeDay.group(1)) {
                case "今天" -> 0;
                case "明天" -> 1;
                case "昨天" -> -1;
                case "前天" -> -2;
                case "大前天" -> -3;
                default -> 0;
            };
            LocalDate date = LocalDate.now(DEFAULT_ZONE).plusDays(offset);
            int hour = parseHour(relativeDay.group(2), relativeDay.group(3));
            return at(date, hour, Integer.parseInt(relativeDay.group(4)));
        }

        // "下午 2:30"
        Matcher dayPart = DAY_PART_TIME.matcher(s);
        if (dayPart.matches()) {
            int hour = dayPartHour(dayPart.group(1), Integer.parseInt(dayPart.group(2)));
            return at(LocalDate.now(DEFAULT_ZONE), hour, Integer.parseInt(dayPart.group(3)));
        }

        // "14:30"
        Matcher bare = BARE_TIME.matcher(s);
        if (bare.matches()) {
            return at(LocalDate.now(DEFAULT_ZONE), Integer.parseInt(bare.group(1)), Integer.parseInt(bare.group(2)));
        }

        // Local date-times: "2024-03-15 14:30:00", "2024/3/15 14:30", "2024-03-15T14:30:00"
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{
                DT_SECONDS_DASH, DT_MINUTES_DASH, DT_SECONDS_SLASH, DT_MINUTES_SLASH,
                DT_SECONDS_T, DT_MINUTES_T}) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(s, formatter);
                return dateTime.atZone(DEFAULT_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // try the next formatter
            }
        }

        // Date only: "2024-03-15" → start of day
        LocalDate date = parseDate(s);
        if (date != null) {
            return date.atStartOfDay(DEFAULT_ZONE).toInstant();
        }

        return null;
    }

    private static LocalDate parseDate(String s) {
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{DATE_DASH, DATE_SLASH}) {
            try {
                return LocalDate.parse(s, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next formatter
            }
        }
        return null;
    }

    private static int parseHour(String dayPart, String hour) {
        int h = Integer.parseInt(hour);
        return dayPart == null ? h : dayPartHour(dayPart, h);
    }

    /**
     * Convert a Chinese day-part plus clock hour into a 24-hour clock value:
     * 下午 2点 → 14, 晚上 8点 → 20, 凌晨 12点 → 0, 中午 3点 → 12.
     */
    private static int dayPartHour(String dayPart, int hour) {
        return switch (dayPart) {
            case "凌晨" -> hour == 12 ? 0 : hour;
            case "早上", "上午" -> hour;
            case "中午" -> 12;
            case "下午" -> hour < 12 ? hour + 12 : hour;
            case "晚上", "夜里" -> hour < 12 ? hour + 12 : hour;
            case "半夜" -> hour == 12 ? 0 : hour;
            default -> hour;
        };
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(DEFAULT_ZONE).toInstant();
    }
}
