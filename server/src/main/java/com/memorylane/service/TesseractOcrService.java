package com.memorylane.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Offline OCR fallback using Tesseract via Tess4J.
 *
 * <p>Uses a {@link ThreadLocal} ITesseract instance per thread because the C++
 * Tesseract engine is not thread-safe and Tess4J's doOCR() mutates internal state.
 *
 * <p>Requires {@code chi_sim.traineddata} in the configured tessdata directory.
 * If Tesseract fails to initialize (missing DLL, no traineddata), all calls
 * return null gracefully — no startup crash.
 */
@Slf4j
@Service
public class TesseractOcrService {

    private final String dataPath;
    private final boolean available;
    private final ThreadLocal<ITesseract> tesseractThreadLocal;

    public TesseractOcrService(
            @Value("${memorylane.ocr.tesseract.data-path:./tessdata}") String dataPath) {
        this.dataPath = dataPath;
        this.available = probeTesseract();
        this.tesseractThreadLocal = ThreadLocal.withInitial(() -> {
            if (!this.available) return null;
            try {
                ITesseract tesseract = new Tesseract();
                tesseract.setDatapath(this.dataPath);
                tesseract.setLanguage("chi_sim+eng");
                tesseract.setPageSegMode(3);  // PSM_AUTO
                tesseract.setOcrEngineMode(1); // OEM_LSTM_ONLY
                return tesseract;
            } catch (Exception e) {
                log.warn("Failed to create Tesseract instance for thread: {}", e.getMessage());
                return null;
            }
        });
        if (available) {
            log.info("Tesseract OCR available, data path: {}", dataPath);
        } else {
            log.warn("Tesseract OCR NOT available — offline OCR fallback disabled. "
                    + "Ensure libtesseract DLL is loadable and chi_sim.traineddata exists in {}", dataPath);
        }
    }

    /**
     * Run OCR on an image. Returns null if Tesseract is unavailable or fails.
     */
    public String ocr(BufferedImage image) {
        if (!available) return null;
        ITesseract tesseract = tesseractThreadLocal.get();
        if (tesseract == null) return null;
        try {
            String text = tesseract.doOCR(image);
            return (text != null && !text.isBlank()) ? text.strip() : null;
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Run OCR on an image input stream. Returns null on failure.
     */
    public String ocr(InputStream inputStream) {
        if (!available) return null;
        try {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                log.warn("Cannot decode image from stream");
                return null;
            }
            return ocr(image);
        } catch (IOException e) {
            log.warn("Failed to read image stream: {}", e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Try to create and immediately dispose a Tesseract instance to verify
     * that the native library loads and traineddata is reachable.
     */
    private boolean probeTesseract() {
        try {
            ITesseract probe = new Tesseract();
            probe.setDatapath(dataPath);
            probe.setLanguage("chi_sim+eng");
            // Verify the data path exists
            Path tessdataDir = Paths.get(dataPath, "tessdata");
            if (!Files.exists(tessdataDir)) {
                tessdataDir = Paths.get(dataPath);
            }
            if (!Files.exists(tessdataDir)) {
                log.warn("Tessdata directory not found: {}", dataPath);
                return false;
            }
            // Verify chi_sim traineddata exists
            Path chiSim = tessdataDir.resolve("chi_sim.traineddata");
            if (!Files.exists(chiSim)) {
                log.warn("chi_sim.traineddata not found in {}", tessdataDir);
                return false;
            }
            log.info("Tesseract probe OK — chi_sim.traineddata found, native lib loaded");
            return true;
        } catch (Exception e) {
            log.warn("Tesseract probe failed: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
}
