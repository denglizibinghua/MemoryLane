package com.memorylane.parser;

import com.memorylane.adapter.model.RawMessage;

import java.util.List;

/**
 * Parses raw clipboard text into structured messages.
 *
 * Pipeline: PlatformDetector → SpeakerExtractor → MessageNormalizer
 */
public interface MessageParser {

    /**
     * Parse raw text from clipboard into structured messages.
     *
     * @param rawContent    the pasted text
     * @param contactHint   optional contact name (null = extract from text)
     * @param platformHint  optional platform hint ("auto" = detect, null = "generic")
     * @return list of parsed messages with platform, speaker, timestamp, content, hash
     */
    List<RawMessage> parse(String rawContent, String contactHint, String platformHint);
}
