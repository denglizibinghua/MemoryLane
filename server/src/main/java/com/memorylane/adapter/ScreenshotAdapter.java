package com.memorylane.adapter;

import com.memorylane.config.DelegatingChatModel;
import com.memorylane.service.PromptTemplateService;
import com.memorylane.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Screenshot input adapter — OCR chat records from screenshots via
 * multimodal LLM. Returns raw text that flows into the existing import pipeline
 * (TextPasteAdapter → parser chain → persist).
 *
 * <p>Uses the currently active {@link DelegatingChatModel} for OCR, so it
 * works with any provider that supports vision.
 *
 * <p>OCR 提示词来自 {@link PromptTemplateService}（key={@code ocr.screenshot.user}）。
 * 模板修改即时生效，无需重启。
 */
@Slf4j
@Component
public class ScreenshotAdapter {

    private final DelegatingChatModel chatModel;
    private final PromptTemplateService promptTemplateService;
    private final TesseractOcrService tesseractOcrService;
    private final boolean fallbackEnabled;

    public ScreenshotAdapter(DelegatingChatModel chatModel,
                             PromptTemplateService promptTemplateService,
                             TesseractOcrService tesseractOcrService,
                             @Value("${memorylane.ocr.fallback-enabled:true}") boolean fallbackEnabled) {
        this.chatModel = chatModel;
        this.promptTemplateService = promptTemplateService;
        this.tesseractOcrService = tesseractOcrService;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * Run OCR on a chat screenshot via multimodal LLM, with optional Tesseract fallback.
     *
     * @param image uploaded screenshot (PNG/JPEG)
     * @return raw extracted text in chat record format
     */
    public String ocr(MultipartFile image) throws Exception {
        try {
            return ocrViaLLM(image);
        } catch (Exception e) {
            if (fallbackEnabled && tesseractOcrService.isAvailable()) {
                log.warn("LLM OCR failed ({}), attempting Tesseract fallback", e.getMessage());
                String tesseractResult = ocrViaTesseract(image);
                if (tesseractResult != null && !tesseractResult.isBlank()) {
                    log.info("Tesseract fallback OK: {} chars", tesseractResult.length());
                    return tesseractResult;
                }
                log.warn("Tesseract fallback also failed");
            }
            throw e;
        }
    }

    private String ocrViaLLM(MultipartFile image) throws Exception {
        log.info("LLM OCR: {} bytes, contentType={}", image.getSize(), image.getContentType());

        byte[] imageBytes = image.getBytes();

        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/png";
        }

        Media media = new Media(MimeTypeUtils.parseMimeType(contentType),
                new ByteArrayResource(imageBytes));
        String ocrPrompt = promptTemplateService.getTemplate("ocr.screenshot.user");
        UserMessage userMessage = new UserMessage(ocrPrompt, List.of(media));

        ChatResponse response = chatModel.call(new Prompt(List.of(userMessage)));
        String text = response.getResult().getOutput().getText();

        if (text == null || text.isBlank()) {
            throw new RuntimeException("OCR 未识别到文字 — 截图可能不含可识别的聊天记录");
        }

        log.info("LLM OCR extracted {} chars", text.length());
        return text.strip();
    }

    private String ocrViaTesseract(MultipartFile image) {
        try {
            BufferedImage buf = ImageIO.read(image.getInputStream());
            if (buf == null) {
                log.warn("Tesseract: cannot decode image");
                return null;
            }
            return tesseractOcrService.ocr(buf);
        } catch (Exception e) {
            log.warn("Tesseract OCR error: {}", e.getMessage());
            return null;
        }
    }
}
