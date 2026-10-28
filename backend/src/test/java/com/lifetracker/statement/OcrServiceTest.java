package com.lifetracker.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OcrServiceTest {

    @Test
    @DisplayName("Test parseTsv: handles normal rows, ignores conf < 0, blank text, non-level 5, and averages confidences")
    void testParseTsv() {
        String tsv = """
            level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
            1\t1\t0\t0\t0\t0\t0\t0\t1000\t1000\t-1\t
            2\t1\t1\t0\t0\t0\t10\t10\t500\t500\t-1\t
            5\t1\t1\t1\t1\t1\t10\t10\t50\t20\t95\t2026-10-01
            5\t1\t1\t1\t1\t2\t65\t10\t60\t20\t85\tCoffee
            5\t1\t1\t1\t1\t3\t130\t10\t40\t20\t-1\t
            5\t1\t1\t1\t1\t4\t180\t10\t50\t20\t90\tShop
            5\t1\t1\t1\t1\t5\t240\t10\t20\t20\t70\t  
            5\t1\t1\t1\t2\t1\t10\t40\t50\t20\t80\t250.00
            """;

        List<ExtractedLine> lines = OcrService.parseTsv(tsv);

        assertThat(lines).hasSize(2);

        // First line: "2026-10-01 Coffee Shop", conf = (95 + 85 + 90) / 3 = 90.0
        ExtractedLine line1 = lines.get(0);
        assertThat(line1.getText()).isEqualTo("2026-10-01 Coffee Shop");
        assertThat(line1.getConfidence()).isEqualTo(90.0);

        // Second line: "250.00", conf = 80.0
        ExtractedLine line2 = lines.get(1);
        assertThat(line2.getText()).isEqualTo("250.00");
        assertThat(line2.getConfidence()).isEqualTo(80.0);
    }
}
