package com.netsimx.persistence;

import com.netsimx.analytics.PerformanceSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exports the collected {@link PerformanceSnapshot} history to a CSV file for offline analysis (e.g. in Excel/pandas). */
public final class CsvExporter {

    private CsvExporter() {}

    public static void export(List<PerformanceSnapshot> history, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(PerformanceSnapshot.csvHeader()).append("\n");
        for (PerformanceSnapshot s : history) {
            sb.append(s.toCsvRow()).append("\n");
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }
}
