package com.netsimx.gui;

import com.netsimx.routing.*;
import com.netsimx.simulation.BenchmarkRunner;
import com.netsimx.topology.TopologyGenerator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * "Benchmark Mode" (Module: separate comparison screen). Runs each
 * selected {@link RoutingAlgorithm} N times over an identical topology
 * and traffic scenario, headlessly (no GUI ticking, no wall-clock
 * pacing), then shows aggregated results in a table and a bar chart so
 * the algorithms can be compared on equal footing.
 *
 * Runs on a background {@link Task} so the UI stays responsive during
 * potentially thousands of simulated ticks; progress is reported back to
 * a progress bar via {@code Platform.runLater}.
 */
public class BenchmarkScreen extends BorderPane {

    public BenchmarkScreen(Runnable onBack) {
        setStyle("-fx-background-color: #0b0f18;");

        CheckBox dijkstraBox = new CheckBox("Dijkstra (OSPF)");
        CheckBox bellmanFordBox = new CheckBox("Bellman-Ford (RIP)");
        CheckBox ecmpBox = new CheckBox("ECMP");
        CheckBox aiBox = new CheckBox("AI Route Optimizer");
        for (CheckBox cb : new CheckBox[]{dijkstraBox, bellmanFordBox, ecmpBox, aiBox}) {
            cb.setSelected(true);
            cb.setStyle("-fx-text-fill: #d6e4f0;");
        }

        ComboBox<String> topologyCombo = new ComboBox<>();
        topologyCombo.getItems().addAll("ISP Backbone (9 routers)", "Mesh (6 routers)", "Tree (13 routers)", "Ring (8 routers)");
        topologyCombo.getSelectionModel().selectFirst();

        Spinner<Integer> runsSpinner = new Spinner<>(1, 200, 20);
        runsSpinner.setEditable(true);
        Spinner<Integer> ticksSpinner = new Spinner<>(20, 2000, 150);
        ticksSpinner.setEditable(true);

        Button runButton = new Button("Run Benchmark");
        runButton.setStyle("-fx-background-color: #3d6fb4; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        Label progressLabel = new Label("Ready");
        progressLabel.setStyle("-fx-text-fill: #7d93ad; -fx-font-size: 11px;");

        TableView<ResultRow> resultsTable = buildResultsTable();
        BarChart<String, Number> chart = buildChart();
        Label winnerLabel = new Label();
        winnerLabel.setStyle("-fx-text-fill: #43cf94; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button back = new Button("\u2190 Back");
        back.setStyle("-fx-background-color: #1b2436; -fx-text-fill: #d6e4f0; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        back.setOnAction(e -> onBack.run());

        runButton.setOnAction(e -> {
            List<RoutingAlgorithm> algorithms = new ArrayList<>();
            if (dijkstraBox.isSelected()) algorithms.add(new DijkstraRouting());
            if (bellmanFordBox.isSelected()) algorithms.add(new BellmanFordRouting());
            if (ecmpBox.isSelected()) algorithms.add(new ECMPRouting());
            if (aiBox.isSelected()) algorithms.add(new com.netsimx.ai.QLearningRouteOptimizer());

            if (algorithms.isEmpty()) {
                progressLabel.setText("Select at least one algorithm.");
                return;
            }

            TopologyGenerator.Template template = switch (topologyCombo.getSelectionModel().getSelectedIndex()) {
                case 1 -> TopologyGenerator.Template.MESH;
                case 2 -> TopologyGenerator.Template.TREE;
                case 3 -> TopologyGenerator.Template.RING;
                default -> TopologyGenerator.Template.ISP_BACKBONE;
            };
            int routerCount = switch (topologyCombo.getSelectionModel().getSelectedIndex()) {
                case 1 -> 6; case 2 -> 13; case 3 -> 8; default -> 9;
            };

            int runs = runsSpinner.getValue();
            int ticks = ticksSpinner.getValue();

            runButton.setDisable(true);
            resultsTable.getItems().clear();
            chart.getData().clear();
            winnerLabel.setText("");

            Task<BenchmarkRunner.BenchmarkReport> task = new Task<>() {
                @Override
                protected BenchmarkRunner.BenchmarkReport call() {
                    BenchmarkRunner runner = new BenchmarkRunner();
                    return runner.run(algorithms,
                            () -> TopologyGenerator.generate(template, routerCount),
                            BenchmarkRunner.randomMixedTraffic(5, System.nanoTime()),
                            runs, ticks,
                            (algoIdx, algoCount, runIdx, runCount) -> Platform.runLater(() -> {
                                double overall = (algoIdx + (double) runIdx / runCount) / algoCount;
                                progressBar.setProgress(overall);
                                progressLabel.setText(String.format("Algorithm %d/%d \u2014 run %d/%d",
                                        algoIdx + 1, algoCount, runIdx + 1, runCount));
                            }));
                }
            };
            task.setOnSucceeded(ev -> {
                BenchmarkRunner.BenchmarkReport report = task.getValue();
                populateResults(resultsTable, chart, report);
                winnerLabel.setText("Winner (lowest avg delay): " + report.winnerByDelay);
                progressLabel.setText("Done \u2014 " + runs + " runs \u00d7 " + algorithms.size() + " algorithm(s)");
                progressBar.setProgress(1.0);
                runButton.setDisable(false);
            });
            task.setOnFailed(ev -> {
                progressLabel.setText("Benchmark failed: " + task.getException());
                runButton.setDisable(false);
            });
            Thread thread = new Thread(task, "benchmark-runner");
            thread.setDaemon(true);
            thread.start();
        });

        VBox configBox = new VBox(14,
                labeled("Choose Algorithms", new HBox(16, dijkstraBox, bellmanFordBox, ecmpBox, aiBox)),
                labeled("Topology", topologyCombo),
                new HBox(24, labeled("Number of Runs", runsSpinner), labeled("Ticks per Run", ticksSpinner)),
                runButton, new HBox(12, progressBar, progressLabel));
        configBox.setPadding(new Insets(24));
        configBox.setPrefWidth(340);

        VBox resultsBox = new VBox(16, sectionTitle("Results"), resultsTable, winnerLabel, sectionTitle("Comparison"), chart);
        resultsBox.setPadding(new Insets(24));
        VBox.setVgrow(chart, Priority.ALWAYS);

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(12));
        setTop(topBar);

        SplitPane split = new SplitPane(configBox, resultsBox);
        split.setDividerPositions(0.28);
        setCenter(split);
    }

    private VBox labeled(String label, javafx.scene.Node content) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #8fb4d6; -fx-font-weight: bold; -fx-font-size: 12px;");
        return new VBox(6, l, content);
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 16px; -fx-font-weight: bold;");
        return l;
    }

    // ------------------------------------------------------------------ //

    public static class ResultRow {
        private final SimpleStringProperty algorithm, avgDelay, avgLoss, avgThroughput, avgUtilization, delivered, dropped;

        ResultRow(BenchmarkRunner.AlgorithmResult r) {
            algorithm = new SimpleStringProperty(r.algorithmName);
            avgDelay = new SimpleStringProperty(String.format("%.2f ms", r.avgDelayMs));
            avgLoss = new SimpleStringProperty(String.format("%.2f%%", r.avgLossRate * 100));
            avgThroughput = new SimpleStringProperty(String.format("%.2f pkt/s", r.avgThroughputPps));
            avgUtilization = new SimpleStringProperty(String.format("%.1f%%", r.avgRouterUtilization * 100));
            delivered = new SimpleStringProperty(String.valueOf(r.totalDelivered));
            dropped = new SimpleStringProperty(String.valueOf(r.totalDropped));
        }

        public String getAlgorithm() { return algorithm.get(); }
        public String getAvgDelay() { return avgDelay.get(); }
        public String getAvgLoss() { return avgLoss.get(); }
        public String getAvgThroughput() { return avgThroughput.get(); }
        public String getAvgUtilization() { return avgUtilization.get(); }
        public String getDelivered() { return delivered.get(); }
        public String getDropped() { return dropped.get(); }
    }

    @SuppressWarnings("unchecked")
    private TableView<ResultRow> buildResultsTable() {
        TableView<ResultRow> table = new TableView<>();
        table.setPrefHeight(200);
        String[][] cols = {
                {"Algorithm", "getAlgorithm"}, {"Avg Delay", "getAvgDelay"}, {"Avg Loss", "getAvgLoss"},
                {"Avg Throughput", "getAvgThroughput"}, {"Avg Utilization", "getAvgUtilization"},
                {"Delivered", "getDelivered"}, {"Dropped", "getDropped"},
        };
        for (String[] c : cols) {
            TableColumn<ResultRow, String> col = new TableColumn<>(c[0]);
            String getter = c[1];
            col.setCellValueFactory(data -> {
                try {
                    return new SimpleStringProperty((String) ResultRow.class.getMethod(getter).invoke(data.getValue()));
                } catch (Exception e) {
                    return new SimpleStringProperty("");
                }
            });
            table.getColumns().add(col);
        }
        table.setPlaceholder(new Label("Run a benchmark to see results."));
        return table;
    }

    private BarChart<String, Number> buildChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Avg End-to-End Delay (ms)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        return chart;
    }

    private void populateResults(TableView<ResultRow> table, BarChart<String, Number> chart,
                                  BenchmarkRunner.BenchmarkReport report) {
        for (var result : report.results) {
            table.getItems().add(new ResultRow(result));
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (var result : report.results) {
            series.getData().add(new XYChart.Data<>(shortName(result.algorithmName), result.avgDelayMs));
        }
        chart.getData().add(series);
    }

    private String shortName(String algorithmName) {
        if (algorithmName.contains("Dijkstra")) return "Dijkstra";
        if (algorithmName.contains("Bellman")) return "Bellman-Ford";
        if (algorithmName.contains("ECMP")) return "ECMP";
        if (algorithmName.contains("AI")) return "AI";
        return algorithmName;
    }
}
