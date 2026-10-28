package com.lifetracker.statement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OcrService {

    private final String tesseractCommand;
    private final int pageTimeoutSeconds;

    public OcrService(
            @Value("${app.statement.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${app.statement.ocr-page-timeout-seconds:120}") int pageTimeoutSeconds) {
        this.tesseractCommand = tesseractCommand;
        this.pageTimeoutSeconds = pageTimeoutSeconds;
    }

    public List<ExtractedLine> extractFromImage(File imageFile, Instant jobStartTime, Duration jobDeadline) {
        if (Duration.between(jobStartTime, Instant.now()).compareTo(jobDeadline) > 0) {
            throw new StatementException("OCR_TIMEOUT");
        }

        ProcessBuilder pb = new ProcessBuilder(
                tesseractCommand,
                imageFile.getAbsolutePath(),
                "stdout",
                "-l", "eng",
                "--psm", "6",
                "tsv"
        );
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new StatementException("OCR_UNAVAILABLE", e);
        }

        CompletableFuture<byte[]> readStdout = CompletableFuture.supplyAsync(() -> {
            try (InputStream is = process.getInputStream()) {
                return is.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });

        try {
            boolean finished = process.waitFor(pageTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                readStdout.cancel(true);
                throw new StatementException("OCR_TIMEOUT");
            }
            if (process.exitValue() != 0) {
                throw new StatementException("OCR_UNAVAILABLE");
            }
            byte[] bytes = readStdout.get(5, TimeUnit.SECONDS);
            String tsv = new String(bytes, StandardCharsets.UTF_8);
            return parseTsv(tsv);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new StatementException("OCR_TIMEOUT", e);
        } catch (StatementException se) {
            throw se;
        } catch (Exception e) {
            throw new StatementException("OCR_UNAVAILABLE", e);
        }
    }

    public static List<ExtractedLine> parseTsv(String tsv) {
        if (tsv == null || tsv.isBlank()) {
            return Collections.emptyList();
        }

        String[] lines = tsv.split("\\r?\\n");
        Map<LineKey, List<WordInfo>> grouped = new LinkedHashMap<>();

        for (String line : lines) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 11) {
                continue;
            }

            int level;
            try {
                level = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                // Header row or non-numeric level
                continue;
            }

            if (level != 5) {
                continue;
            }

            String text = parts.length > 11 ? parts[11].trim() : "";
            if (text.isEmpty()) {
                continue;
            }

            double conf;
            try {
                conf = Double.parseDouble(parts[10].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            if (conf < 0) {
                continue;
            }

            try {
                int pageNum = Integer.parseInt(parts[1].trim());
                int blockNum = Integer.parseInt(parts[2].trim());
                int parNum = Integer.parseInt(parts[3].trim());
                int lineNum = Integer.parseInt(parts[4].trim());
                LineKey key = new LineKey(pageNum, blockNum, parNum, lineNum);
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new WordInfo(text, conf));
            } catch (NumberFormatException ignored) {
            }
        }

        List<ExtractedLine> result = new ArrayList<>();
        for (List<WordInfo> words : grouped.values()) {
            if (words.isEmpty()) {
                continue;
            }
            String lineText = words.stream().map(w -> w.text).collect(Collectors.joining(" "));
            double avgConf = words.stream().mapToDouble(w -> w.conf).average().orElse(0.0);
            result.add(new ExtractedLine(lineText, avgConf));
        }

        return result;
    }

    private static class LineKey {
        final int pageNum;
        final int blockNum;
        final int parNum;
        final int lineNum;

        LineKey(int pageNum, int blockNum, int parNum, int lineNum) {
            this.pageNum = pageNum;
            this.blockNum = blockNum;
            this.parNum = parNum;
            this.lineNum = lineNum;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LineKey lineKey = (LineKey) o;
            return pageNum == lineKey.pageNum &&
                    blockNum == lineKey.blockNum &&
                    parNum == lineKey.parNum &&
                    lineNum == lineKey.lineNum;
        }

        @Override
        public int hashCode() {
            return Objects.hash(pageNum, blockNum, parNum, lineNum);
        }
    }

    private static class WordInfo {
        final String text;
        final double conf;

        WordInfo(String text, double conf) {
            this.text = text;
            this.conf = conf;
        }
    }
}
