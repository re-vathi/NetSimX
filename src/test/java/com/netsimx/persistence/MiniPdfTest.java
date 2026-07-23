package com.netsimx.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MiniPdfTest {

    @Test
    void producesAFileStartingWithThePdfHeaderAndEndingWithEof(@TempDir Path tempDir) throws IOException {
        Path out = tempDir.resolve("test.pdf");
        new MiniPdf().addTitle("Test Report").addLine("Hello world").writeTo(out);

        assertTrue(Files.exists(out));
        byte[] bytes = Files.readAllBytes(out);
        String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(content.startsWith("%PDF-1.4"), "PDF must start with a valid header");
        assertTrue(content.trim().endsWith("%%EOF"), "PDF must end with the EOF marker");
        assertTrue(content.contains("xref"), "PDF must contain a cross-reference table");
        assertTrue(content.contains("/Type /Catalog"), "PDF must declare a document catalog");
    }

    @Test
    void specialCharactersAreEscapedInTheRawStream(@TempDir Path tempDir) throws IOException {
        Path out = tempDir.resolve("special.pdf");
        new MiniPdf().addLine("Value (with parens) and a \\backslash\\").writeTo(out);

        String content = Files.readString(out, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("\\(with parens\\)"), "Parentheses must be escaped for the PDF text-showing operator");
        assertTrue(content.contains("\\\\backslash\\\\"), "Backslashes must be escaped");
    }

    @Test
    void longContentSplitsAcrossMultiplePages(@TempDir Path tempDir) throws IOException {
        Path out = tempDir.resolve("long.pdf");
        MiniPdf pdf = new MiniPdf().addTitle("Long Report");
        for (int i = 0; i < 80; i++) {
            pdf.addLine("Line " + i);
        }
        pdf.writeTo(out);

        String content = Files.readString(out, java.nio.charset.StandardCharsets.ISO_8859_1);
        // Two (or more) /Page objects means pagination actually happened, not just one giant page.
        long pageObjectCount = content.lines().filter(l -> l.contains("/Type /Page ")).count();
        assertTrue(pageObjectCount >= 2, "80 lines should overflow a single US Letter page");
    }

    @Test
    void emptyReportStillProducesAValidSinglePage(@TempDir Path tempDir) throws IOException {
        Path out = tempDir.resolve("empty.pdf");
        new MiniPdf().writeTo(out);
        assertTrue(Files.size(out) > 0);
    }
}
