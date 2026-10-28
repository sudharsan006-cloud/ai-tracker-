package com.lifetracker.statement;

import com.lifetracker.config.ConfigService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StatementProcessingPipelineTest {

    @TempDir
    Path tempDir;

    private StatementImportRepository importRepository;
    private StatementImportRowRepository rowRepository;
    private StatementParserRegistry parserRegistry;
    private StatementMatcher statementMatcher;
    private ConfigService configService;
    private JdbcTemplate jdbcTemplate;
    private OcrService ocrService;
    private TextExtractor textExtractor;
    private StatementProcessingService processingService;

    @BeforeEach
    void setUp() {
        importRepository = mock(StatementImportRepository.class);
        rowRepository = mock(StatementImportRowRepository.class);
        configService = mock(ConfigService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        statementMatcher = mock(StatementMatcher.class);

        when(configService.get("statement_max_pages")).thenReturn(new BigDecimal("10"));
        when(configService.get("statement_ocr_min_confidence")).thenReturn(new BigDecimal("80"));
        when(statementMatcher.getUserTimezone(any())).thenReturn("Asia/Kolkata");

        ocrService = new OcrService("tesseract", 120);
        textExtractor = new TextExtractor(ocrService, configService, tempDir.toString());

        FakeTestStatementParser fakeParser = new FakeTestStatementParser("SAMPLE");
        parserRegistry = new StatementParserRegistry(List.of(fakeParser));

        processingService = new StatementProcessingService(
                importRepository,
                rowRepository,
                parserRegistry,
                textExtractor,
                statementMatcher,
                configService,
                jdbcTemplate,
                6
        );
    }

    private File createTestPdf(String lineText, String password) throws IOException {
        File pdfFile = tempDir.resolve("test_" + UUID.randomUUID() + ".pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                // Ensure > 100 non-whitespace characters to hit pdf_text extraction
                stream.showText(lineText + " | statement padding non-whitespace content for header footer page details 1234567890");
                stream.endText();
            }

            if (password != null && !password.isEmpty()) {
                AccessPermission ap = new AccessPermission();
                StandardProtectionPolicy spp = new StandardProtectionPolicy(password, "", ap);
                spp.setEncryptionKeyLength(128);
                doc.protect(spp);
            }

            doc.save(pdfFile);
        }
        return pdfFile;
    }

    @Test
    @DisplayName("Test 5: End-to-end text PDF extraction method is pdf_text and confidence 100")
    void testEndToEndTextPdf() throws Exception {
        File pdf = createTestPdf("2026-10-01|Coffee Shop|250.00|debit|1000.00", null);

        ExtractedText extracted = textExtractor.extract(pdf, "pdf", null, Instant.now(), Duration.ofMinutes(6));

        assertThat(extracted.getMethod()).isEqualTo("pdf_text");
        assertThat(extracted.getLines()).isNotEmpty();
        assertThat(extracted.getLines().get(0).getConfidence()).isEqualTo(100.0);

        ParsedStatement parsed = parserRegistry.find("SAMPLE").orElseThrow().parse(extracted.getLines());
        assertThat(parsed.rows).hasSize(1);
        assertThat(parsed.rows.get(0).amount).isEqualTo(new BigDecimal("250.00"));
        assertThat(parsed.rows.get(0).direction).isEqualTo("debit");
    }

    @Test
    @DisplayName("Test 6a: Password-protected PDF without password throws PASSWORD_REQUIRED")
    void testPasswordProtectedPdfNoPassword() throws Exception {
        File pdf = createTestPdf("2026-10-01|Coffee Shop|250.00|debit|1000.00", "mysecret");

        assertThatThrownBy(() -> textExtractor.extract(pdf, "pdf", null, Instant.now(), Duration.ofMinutes(6)))
                .isInstanceOf(StatementException.class)
                .matches(e -> "PASSWORD_REQUIRED".equals(((StatementException) e).getErrorCode()));
    }

    @Test
    @DisplayName("Test 6b: Password-protected PDF with wrong password throws WRONG_PASSWORD")
    void testPasswordProtectedPdfWrongPassword() throws Exception {
        File pdf = createTestPdf("2026-10-01|Coffee Shop|250.00|debit|1000.00", "mysecret");

        assertThatThrownBy(() -> textExtractor.extract(pdf, "pdf", "wrongpass", Instant.now(), Duration.ofMinutes(6)))
                .isInstanceOf(StatementException.class)
                .matches(e -> "WRONG_PASSWORD".equals(((StatementException) e).getErrorCode()));
    }

    @Test
    @DisplayName("Test 6c: Password-protected PDF with correct password succeeds")
    void testPasswordProtectedPdfCorrectPassword() throws Exception {
        File pdf = createTestPdf("2026-10-01|Coffee Shop|250.00|debit|1000.00", "mysecret");

        ExtractedText extracted = textExtractor.extract(pdf, "pdf", "mysecret", Instant.now(), Duration.ofMinutes(6));
        assertThat(extracted.getMethod()).isEqualTo("pdf_text");
        assertThat(extracted.getLines()).isNotEmpty();
    }

    @Test
    @DisplayName("Test 7a: Bank with no registered parser ends as failed with NO_PARSER")
    void testNoParserFound() {
        UUID importId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setBankAccountId(accountId);
        imp.setFileType("pdf");

        when(importRepository.findById(importId)).thenReturn(Optional.of(imp));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(accountId))).thenReturn("UNKNOWN_BANK");

        File dummyFile = tempDir.resolve("dummy.pdf").toFile();

        processingService.process(importId, dummyFile.getAbsolutePath(), null);

        assertThat(imp.getStatus()).isEqualTo("failed");
        assertThat(imp.getErrorCode()).isEqualTo("NO_PARSER");
    }

    @Test
    @DisplayName("Test 7b: Parser returning 0 rows ends as failed with NO_ROWS_FOUND")
    void testParserReturningZeroRowsEndsAsFailed() throws Exception {
        File pdf = createTestPdf("No valid row format here at all", null);
        UUID importId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setBankAccountId(accountId);
        imp.setFileType("pdf");

        when(importRepository.findById(importId)).thenReturn(Optional.of(imp));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(accountId))).thenReturn("SAMPLE");

        processingService.process(importId, pdf.getAbsolutePath(), null);

        assertThat(imp.getStatus()).isEqualTo("failed");
        assertThat(imp.getErrorCode()).isEqualTo("NO_ROWS_FOUND");
    }

    @Test
    @DisplayName("Test 12: OCR integration test on rendered PNG (skipped if tesseract not installed)")
    void testOcrIntegrationOnImage() throws Exception {
        boolean tesseractAvailable = false;
        try {
            Process p = new ProcessBuilder("tesseract", "--version").start();
            tesseractAvailable = (p.waitFor() == 0);
        } catch (Exception ignored) {
        }

        Assumptions.assumeTrue(tesseractAvailable, "Tesseract binary is not installed on this system");

        BufferedImage img = new BufferedImage(400, 150, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 150);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 28));
        g.drawString("Amount 250.00", 30, 80);
        g.dispose();

        File imgFile = tempDir.resolve("test_ocr.png").toFile();
        ImageIO.write(img, "png", imgFile);

        List<ExtractedLine> lines = ocrService.extractFromImage(imgFile, Instant.now(), Duration.ofMinutes(2));
        assertThat(lines).isNotEmpty();

        boolean foundAmount = lines.stream().anyMatch(l -> l.getText().contains("250.00") || l.getText().contains("Amount"));
        assertThat(foundAmount).isTrue();
    }

    @Test
    @DisplayName("Test 13: Temp file is deleted after processing (success and failure)")
    void testTempFileIsDeletedAfterProcessing() throws Exception {
        File dummyFile = tempDir.resolve("temp_file_" + UUID.randomUUID() + ".pdf").toFile();
        dummyFile.createNewFile();
        assertThat(dummyFile.exists()).isTrue();

        UUID importId = UUID.randomUUID();
        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setBankAccountId(UUID.randomUUID());
        imp.setFileType("pdf");

        when(importRepository.findById(importId)).thenReturn(Optional.of(imp));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(UUID.class))).thenReturn("SAMPLE");

        processingService.process(importId, dummyFile.getAbsolutePath(), null);

        // Temp file should be deleted in finally block
        assertThat(dummyFile.exists()).isFalse();
    }
}
