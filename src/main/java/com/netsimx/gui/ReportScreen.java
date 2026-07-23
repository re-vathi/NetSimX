package com.netsimx.gui;

import com.netsimx.persistence.CsvExporter;
import com.netsimx.persistence.MiniPdf;
import com.netsimx.simulation.SimulationEngine;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Report Screen" (Module 12 wireframe) - a point-in-time summary of a
 * completed or in-progress simulation run, with Download PDF / Download
 * CSV actions. Pulls every figure directly from the live
 * {@link SimulationEngine} rather than a separately-tracked shadow
 * state, so the report always reflects exactly what the dashboard itself
 * is showing.
 */
public class ReportScreen extends BorderPane {

    public ReportScreen(SimulationEngine engine, String topologyName, Runnable onBack) {
        setStyle("-fx-background-color: #0b0f18;");

        Map<String, String> summary = buildSummary(engine, topologyName);

        VBox content = new VBox(6);
        content.setPadding(new Insets(30, 60, 30, 60));
        content.setMaxWidth(640);

        Label title = new Label("Simulation Summary");
        title.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 24px; -fx-font-weight: bold;");
        content.getChildren().add(title);
        content.getChildren().add(new Separator());

        for (var entry : summary.entrySet()) {
            HBox row = new HBox(10);
            Label key = new Label(entry.getKey());
            key.setPrefWidth(220);
            key.setStyle("-fx-text-fill: #8fb4d6; -fx-font-size: 13px; -fx-font-weight: bold;");
            Label value = new Label(entry.getValue());
            value.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 13px;");
            row.getChildren().addAll(key, value);
            content.getChildren().add(row);
        }

        HBox buttons = new HBox(12);
        buttons.setPadding(new Insets(24, 0, 0, 0));

        Button downloadPdf = new Button("Download PDF");
        downloadPdf.setStyle(primaryButtonStyle());
        downloadPdf.setOnAction(e -> exportPdf(summary, topologyName, downloadPdf.getScene().getWindow()));

        Button downloadCsv = new Button("Download CSV");
        downloadCsv.setStyle(secondaryButtonStyle());
        downloadCsv.setOnAction(e -> exportCsv(engine, downloadCsv.getScene().getWindow()));

        buttons.getChildren().addAll(downloadPdf, downloadCsv);
        content.getChildren().add(buttons);

        Button back = new Button("\u2190 Back to Workspace");
        back.setStyle(secondaryButtonStyle());
        back.setOnAction(e -> onBack.run());
        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(12));
        setTop(topBar);

        setCenter(content);
    }

    private Map<String, String> buildSummary(SimulationEngine engine, String topologyName) {
        Map<String, String> m = new LinkedHashMap<>();
        var stats = engine.getStatistics();
        var history = stats.getHistory();

        m.put("Topology", topologyName);
        m.put("Routing Algorithm", engine.getRoutingAlgorithm().getName());
        m.put("Simulation Time", String.format(Locale.US, "%,d ms", engine.getSimTimeMs()));
        m.put("Packets Generated", String.valueOf(stats.getTotalGenerated()));
        m.put("Packets Delivered", String.valueOf(stats.getTotalDelivered()));
        long attempted = stats.getTotalDelivered() + stats.getTotalDropped();
        double lossPct = attempted == 0 ? 0 : (100.0 * stats.getTotalDropped() / attempted);
        m.put("Packet Loss", String.format(Locale.US, "%.2f%% (%d dropped)", lossPct, stats.getTotalDropped()));

        if (!history.isEmpty()) {
            var last = history.get(history.size() - 1);
            m.put("Average Delay", String.format(Locale.US, "%.1f ms", last.avgEndToEndDelayMs));
            m.put("Bandwidth Utilization", String.format(Locale.US, "Router %.1f%% / Link %.1f%%",
                    last.avgRouterUtilization * 100, last.avgLinkUtilization * 100));
        } else {
            m.put("Average Delay", "N/A (no samples yet)");
            m.put("Bandwidth Utilization", "N/A (no samples yet)");
        }

        m.put("Congestion Events", String.valueOf(engine.getQueueManager().getCongestionEventCount()));
        m.put("Failures Triggered", String.valueOf(engine.getFailureSimulator().getTotalFailureEvents()));
        m.put("TCP Retransmissions", String.valueOf(engine.getTcpUdpManager().getRetransmissionCount()));

        if (engine.getRoutingAlgorithm() instanceof com.netsimx.ai.QLearningRouteOptimizer ai) {
            m.put("AI Decisions (training steps)", String.valueOf(ai.getTrainingSteps()));
        }

        return m;
    }

    private void exportPdf(Map<String, String> summary, String topologyName, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Report as PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("netsimx-report.pdf");
        var file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try {
            MiniPdf pdf = new MiniPdf();
            pdf.addTitle("NetSimX Simulation Report").addBlankLine();
            pdf.addHeading(topologyName).addBlankLine();
            for (var entry : summary.entrySet()) {
                pdf.addLine(entry.getKey() + ":  " + entry.getValue());
            }
            pdf.writeTo(file.toPath());
        } catch (IOException ex) {
            showError("Failed to export PDF", ex.getMessage());
        }
    }

    private void exportCsv(SimulationEngine engine, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Performance History as CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("netsimx-report.csv");
        var file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try {
            CsvExporter.export(engine.getStatistics().getHistory(), file.toPath());
        } catch (IOException ex) {
            showError("Failed to export CSV", ex.getMessage());
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String primaryButtonStyle() {
        return "-fx-background-color: #3d6fb4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 22; -fx-background-radius: 6; -fx-cursor: hand;";
    }

    private String secondaryButtonStyle() {
        return "-fx-background-color: #1b2436; -fx-text-fill: #d6e4f0; -fx-padding: 10 22; -fx-background-radius: 6; -fx-border-color: #2a3650; -fx-border-radius: 6; -fx-cursor: hand;";
    }
}
