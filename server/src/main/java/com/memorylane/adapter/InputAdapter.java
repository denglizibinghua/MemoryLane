package com.memorylane.adapter;

import com.memorylane.adapter.model.RawMessage;

import java.util.List;

/**
 * Input adapter interface — pluggable input sources.
 *
 * v1: TextPasteAdapter (text clipboard)
 * v2: ScreenshotAdapter (OCR via multimodal LLM)
 */
public interface InputAdapter {

    /**
     * Parse raw input text into structured messages.
     *
     * @param rawContent raw text from clipboard
     * @param contactHint optional contact name hint (null = auto-detect)
     * @param platformHint optional platform hint (null = auto-detect)
     * @return parsed messages with platform, speaker, timestamp, content
     */
    List<RawMessage> parse(String rawContent, String contactHint, String platformHint);
}
