package com.lifetracker.statement;

import com.lifetracker.config.ConfigService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class TextExtractor {

    private final OcrService ocrService;
    private final ConfigService configService;
    private final String tmpDir;

    public TextExtractor(
            OcrService ocrService,
            ConfigService configService,
            @Value("${app.statement.tmp-dir:${java.io.tmpdir}}") String tmpDir) {
        this.ocrService = ocrService;
        this.configService = configService;
        this.tmpDir = tmpDir;
    }

    public ExtractedText extract(File file, String fileType, String password, Instant jobStartTime, Duration jobDeadline) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return extractPdf(file, password, jobStartTime, jobDeadline);
        } else if ("png".equalsIgnoreCase(fileType) || "jpg".equalsIgnoreCase(fileType)) {
            List<ExtractedLine> lines = ocrService.extractFromImage(file, jobStartTime, jobDeadline);
            return new ExtractedText(lines, "ocr", 1);
        } else {
            throw new StatementException("UNREADABLE_FILE", "Unsupported file type: " + fileType);
        }
    }

    private ExtractedText extractPdf(File file, String password, Instant jobStartTime, Duration jobDeadline) {
        PDDocument pdDoc = null;
        try {
            pdDoc = Loader.loadPDF(file, password != null ? password : "");
        } catch (InvalidPasswordException e) {
            if (password == null || password.isBlank()) {
                throw new StatementException("PASSWORD_REQUIRED");
            } else {
                throw new StatementException("WRONG_PASSWORD");
            }
        } catch (IOException e) {
            throw new StatementException("UNREADABLE_FILE", e);
        }

        try {
            int pageCount = pdDoc.getNumberOfPages();
            int maxPages = configService.get("statement_max_pages").intValue();
            if (pageCount > maxPages) {
                throw new StatementException("TOO_MANY_PAGES");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdDoc);

            long nonWsCount = text.chars().filter(ch -> !Character.isWhitespace(ch)).count();
            if (pageCount > 0 && ((double) nonWsCount / pageCount) >= 100.0) {
                String[] rawLines = text.split("\\r?\\n");
                List<ExtractedLine> lines = Arrays.stream(rawLines)
                        .map(String::trim)
                        .filter(l -> !l.isEmpty())
                        .map(l -> new ExtractedLine(l, 100.0))
                        .toList();
                return new ExtractedText(lines, "pdf_text", pageCount);
            }

            // Fallback to OCR
            PDFRenderer renderer = new PDFRenderer(pdDoc);
            List<ExtractedLine> allOcrLines = new ArrayList<>();
            File tempDirectory = new File(tmpDir);
            if (!tempDirectory.exists()) {
                tempDirectory.mkdirs();
            }

            for (int i = 0; i < pageCount; i++) {
                if (Duration.between(jobStartTime, Instant.now()).compareTo(jobDeadline) > 0) {
                    throw new StatementException("OCR_TIMEOUT");
                }
                BufferedImage image = renderer.renderImageWithDPI(i, 200, ImageType.GRAY);
                File pagePng = new File(tempDirectory, UUID.randomUUID() + ".png");
                try {
                    ImageIO.write(image, "png", pagePng);
                    allOcrLines.addAll(ocrService.extractFromImage(pagePng, jobStartTime, jobDeadline));
                } catch (IOException e) {
                    throw new StatementException("UNREADABLE_FILE", e);
                } finally {
                    if (pagePng.exists()) {
                        pagePng.delete();
                    }
                }
            }

            return new ExtractedText(allOcrLines, "ocr", pageCount);
        } catch (IOException e) {
            throw new StatementException("UNREADABLE_FILE", e);
        } finally {
            if (pdDoc != null) {
                try {
                    pdDoc.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
