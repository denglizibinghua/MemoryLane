package com.memorylane.parser;

import com.memorylane.adapter.model.RawMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link MessageParser} implementation.
 *
 * <p>Orchestrates the three-stage text parsing pipeline:
 * {@link PlatformDetector} → {@link SpeakerExtractor} → {@link MessageNormalizer}.
 *
 * <p>Pure regex/text processing — no LLM calls and no database access.
 */
@Component
@Slf4j
public class DefaultMessageParser implements MessageParser {

    private final PlatformDetector platformDetector;
    private final SpeakerExtractor speakerExtractor;
    private final MessageNormalizer messageNormalizer;

    /**
     * @param platformDetector detects the platform format from raw text
     * @param speakerExtractor splits text into messages with speaker/time/content
     * @param messageNormalizer stamps content hashes and default platform
     */
    public DefaultMessageParser(PlatformDetector platformDetector,
                                SpeakerExtractor speakerExtractor,
                                MessageNormalizer messageNormalizer) {
        this.platformDetector = platformDetector;
        this.speakerExtractor = speakerExtractor;
        this.messageNormalizer = messageNormalizer;
    }

    /**
     * Parse raw clipboard text into structured messages.
     *
     * @param rawContent    the pasted text
     * @param contactHint   optional contact name (null = extract from text)
     * @param platformHint  optional platform hint; {@code "auto"} or {@code null}
     *                      triggers format auto-detection, any other value is
     *                      used directly (wechat | qq | douyin | sms | generic)
     * @return list of parsed messages with platform, speaker, timestamp, content
     *         and content hash; empty when the raw content is blank
     */
    @Override
    public List<RawMessage> parse(String rawContent, String contactHint, String platformHint) {
        if (rawContent == null || rawContent.isBlank()) {
            log.debug("parse: empty raw content, returning empty list");
            return List.of();
        }

        String platform = resolvePlatform(rawContent, platformHint);
        log.debug("parse: platform={} for {} chars of raw content", platform, rawContent.length());

        List<RawMessage> extracted = speakerExtractor.extract(rawContent, platform, contactHint);
        log.debug("parse: extracted {} raw messages", extracted.size());

        List<RawMessage> normalized = messageNormalizer.normalize(extracted, platform);
        log.debug("parse: normalized {} messages", normalized.size());

        return normalized;
    }

    private String resolvePlatform(String rawContent, String platformHint) {
        if (platformHint == null || platformHint.isBlank() || "auto".equalsIgnoreCase(platformHint.trim())) {
            return platformDetector.detect(rawContent);
        }
        return platformHint.trim().toLowerCase();
    }
}
