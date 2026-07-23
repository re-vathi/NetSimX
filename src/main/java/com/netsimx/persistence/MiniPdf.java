package com.netsimx.persistence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A small, dependency-free PDF writer for simple text reports (Module:
 * Report Screen -&gt; Download PDF). Hand-generates a valid PDF 1.4 file
 * (header, objects, cross-reference table, trailer) using only the
 * standard non-embedded Helvetica/Helvetica-Bold base fonts - no external
 * library needed, consistent with the rest of this project's
 * dependency-free persistence approach (see {@link MiniJson}).
 *
 * Deliberately supports only what a simulation report needs: a title, a
 * section heading style, and plain body lines, with automatic pagination
 * once content overflows a US Letter page. This is not a general-purpose
 * PDF library - for anything beyond simple text reports, a real library
 * (e.g. Apache PDFBox) would be the right tool.
 */
public final class MiniPdf {

    private static final double PAGE_WIDTH = 612;   // US Letter, points
    private static final double PAGE_HEIGHT = 792;
    private static final double MARGIN = 56;
    private static final double LINE_HEIGHT = 16;

    private enum LineStyle { TITLE, HEADING, BODY }

    private record Line(String text, LineStyle style) {}

    private final List<Line> lines = new ArrayList<>();

    public MiniPdf addTitle(String text) {
        lines.add(new Line(text, LineStyle.TITLE));
        return this;
    }

    public MiniPdf addHeading(String text) {
        lines.add(new Line(text, LineStyle.HEADING));
        return this;
    }

    public MiniPdf addLine(String text) {
        lines.add(new Line(text, LineStyle.BODY));
        return this;
    }

    public MiniPdf addBlankLine() {
        lines.add(new Line("", LineStyle.BODY));
        return this;
    }

    /** Paginates {@link #lines} into pages, then writes a complete, valid PDF file to {@code path}. */
    public void writeTo(Path path) throws IOException {
        List<List<Line>> pages = paginate();
        byte[] pdfBytes = buildPdfDocument(pages);
        Files.write(path, pdfBytes);
    }

    private List<List<Line>> paginate() {
        List<List<Line>> pages = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        double y = PAGE_HEIGHT - MARGIN;
        double usableHeight = PAGE_HEIGHT - 2 * MARGIN;
        int maxLinesPerPage = (int) (usableHeight / LINE_HEIGHT);

        for (Line line : lines) {
            if (current.size() >= maxLinesPerPage) {
                pages.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) pages.add(current);
        if (pages.isEmpty()) pages.add(new ArrayList<>());
        return pages;
    }

    // ------------------------------------------------------------------ //
    // Raw PDF assembly
    // ------------------------------------------------------------------ //

    private byte[] buildPdfDocument(List<List<Line>> pages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>(); // byte offset of each object, index 0 unused (object numbers are 1-based)
        offsets.add(0);

        writeRaw(out, "%PDF-1.4\n");

        int catalogObj = 1;
        int pagesObj = 2;
        int fontRegularObj = 3;
        int fontBoldObj = 4;
        int firstPageContentObj = 5;
        int firstPageObj = firstPageContentObj + pages.size(); // page objects come after all content-stream objects

        // 1: Catalog
        offsets.add(out.size());
        writeRaw(out, obj(catalogObj, "<< /Type /Catalog /Pages " + pagesObj + " 0 R >>"));

        // 2: Pages (kids filled in after we know page object numbers)
        List<Integer> pageObjNumbers = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) pageObjNumbers.add(firstPageObj + i);
        StringBuilder kids = new StringBuilder();
        for (int n : pageObjNumbers) kids.append(n).append(" 0 R ");
        offsets.add(out.size());
        writeRaw(out, obj(pagesObj, "<< /Type /Pages /Count " + pages.size() + " /Kids [ " + kids + "] >>"));

        // 3/4: Fonts
        offsets.add(out.size());
        writeRaw(out, obj(fontRegularObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
        offsets.add(out.size());
        writeRaw(out, obj(fontBoldObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"));

        // Content streams, one per page
        for (List<Line> pageLines : pages) {
            String content = buildContentStream(pageLines);
            byte[] contentBytes = content.getBytes(StandardCharsets.ISO_8859_1);
            offsets.add(out.size());
            writeRaw(out, "" + (offsets.size() - 1) + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            out.write(contentBytes);
            writeRaw(out, "\nendstream\nendobj\n");
        }

        // Page objects
        for (int i = 0; i < pages.size(); i++) {
            int contentObjNum = firstPageContentObj + i;
            offsets.add(out.size());
            writeRaw(out, obj(firstPageObj + i,
                    "<< /Type /Page /Parent " + pagesObj + " 0 R /MediaBox [0 0 " + (int) PAGE_WIDTH + " " + (int) PAGE_HEIGHT + "] " +
                            "/Resources << /Font << /F1 " + fontRegularObj + " 0 R /F2 " + fontBoldObj + " 0 R >> >> " +
                            "/Contents " + contentObjNum + " 0 R >>"));
        }

        int xrefStart = out.size();
        int totalObjects = offsets.size(); // includes the unused index-0 slot
        writeRaw(out, "xref\n0 " + totalObjects + "\n");
        writeRaw(out, "0000000000 65535 f \n");
        for (int i = 1; i < totalObjects; i++) {
            writeRaw(out, String.format("%010d 00000 n \n", offsets.get(i)));
        }
        writeRaw(out, "trailer\n<< /Size " + totalObjects + " /Root " + catalogObj + " 0 R >>\n");
        writeRaw(out, "startxref\n" + xrefStart + "\n%%EOF");

        return out.toByteArray();
    }

    private String buildContentStream(List<Line> pageLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n");
        double y = PAGE_HEIGHT - MARGIN;
        boolean first = true;
        String currentFont = null;

        for (Line line : pageLines) {
            String font = switch (line.style()) {
                case TITLE -> "/F2 18 Tf";
                case HEADING -> "/F2 13 Tf";
                case BODY -> "/F1 10.5 Tf";
            };
            if (!font.equals(currentFont)) {
                sb.append(font).append("\n");
                currentFont = font;
            }
            if (first) {
                sb.append(MARGIN).append(" ").append(y).append(" Td\n");
                first = false;
            } else {
                sb.append("0 -").append(LINE_HEIGHT).append(" Td\n");
            }
            sb.append("(").append(escape(line.text())).append(") Tj\n");
        }
        sb.append("ET");
        return sb.toString();
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String obj(int number, String body) {
        return number + " 0 obj\n" + body + "\nendobj\n";
    }

    private void writeRaw(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
