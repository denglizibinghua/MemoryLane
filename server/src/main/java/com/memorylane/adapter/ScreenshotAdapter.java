package com.memorylane.adapter;

import com.memorylane.config.DelegatingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * v2 screenshot input adapter — OCR chat records from screenshots via
 * multimodal LLM. Returns raw text that flows into the existing import pipeline
 * (TextPasteAdapter → parser chain → persist).
 *
 * <p>Uses the currently active {@link DelegatingChatModel} for OCR, so it
 * works with any provider that supports vision (OpenAI GPT-4o, DeepSeek V3,
 * Qwen-VL, GLM-4V, Claude 3.5, etc.).
 */
@Slf4j
@Component
public class ScreenshotAdapter {

    private static final String OCR_PROMPT = """
            请识别这张聊天记录截图中的所有文字。按对话格式逐条输出，每条消息一行。
            如果能看到时间戳，保留原始时间格式。
            格式示例：
            张三: 2024-01-15 14:30:00 你在干嘛
            李四: 2024-01-15 14:31:00 刚吃完饭
            
            注意：
            - 不要添加额外解释，只输出对话内容
            - 保持说话人和内容的对应关系
            - 如果截图中有群聊名称，请在开头用「群聊：名称」标注
            """;

    private final DelegatingChatModel chatModel;

    public ScreenshotAdapter(DelegatingChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Run OCR on a chat screenshot via multimodal LLM.
     *
     * @param image uploaded screenshot (PNG/JPEG)
     * @return raw extracted text in chat record format
     */
    public String ocr(MultipartFile image) throws Exception {
        log.info("Screenshot OCR: {} bytes, contentType={}", image.getSize(), image.getContentType());

        byte[] imageBytes = image.getBytes();

        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/png";
        }

        Media media = new Media(MimeTypeUtils.parseMimeType(contentType),
                new ByteArrayResource(imageBytes));
        UserMessage userMessage = new UserMessage(OCR_PROMPT, List.of(media));

        ChatResponse response = chatModel.call(new Prompt(List.of(userMessage)));
        String text = response.getResult().getOutput().getText();

        if (text == null || text.isBlank()) {
            throw new RuntimeException("OCR 未识别到文字 — 截图可能不含可识别的聊天记录");
        }

        log.info("OCR extracted {} chars", text.length());
        return text.strip();
    }
}
