package com.netsimx.gui;

import com.netsimx.analytics.PerformanceSnapshot;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Module 11/12 - live-updating throughput/latency/PDR line charts, fed one
 * {@link PerformanceSnapshot} at a time as the simulation ticks. Keeps a
 * bounded number of visible points so the charts stay readable during
 * long-running simulations (older points scroll off the left).
 */
public class ChartsPanel extends VBox {

    private static final int MAX_POINTS = 120;

    private final XYChart.Series<Number, Number> throughputSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> latencySeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> pdrSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> utilSeries = new XYChart.Series<>();

    private int sampleIndex = 0;

    public ChartsPanel() {
        setSpacing(8);
        setPadding(new Insets(4));

        LineChart<Number, Number> throughputChart = buildChart("Throughput (pkt/s)", throughputSeries);
        LineChart<Number, Number> latencyChart = buildChart("Avg End-to-End Delay (ms)", latencySeries);
        LineChart<Number, Number> pdrChart = buildChart("Packet Delivery Ratio", pdrSeries);
        LineChart<Number, Number> utilChart = buildChart("Avg Router / Link Utilization", utilSeries);

        for (LineChart<Number, Number> c : java.util.List.of(throughputChart, latencyChart, pdrChart, utilChart)) {
            c.setPrefHeight(160);
            c.setAnimated(false);
            VBox.setVgrow(c, Priority.ALWAYS);
        }

        getChildren().addAll(throughputChart, latencyChart, pdrChart, utilChart);
    }

    private LineChart<Number, Number> buildChart(String title, XYChart.Series<Number, Number> series) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Sample");
        NumberAxis yAxis = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.getData().add(series);
        return chart;
    }

    public void addSnapshot(PerformanceSnapshot snap) {
        sampleIndex++;
        addPoint(throughputSeries, snap.throughputPacketsPerSec);
        addPoint(latencySeries, snap.avgEndToEndDelayMs);
        addPoint(pdrSeries, snap.packetDeliveryRatio);
        addPoint(utilSeries, (snap.avgRouterUtilization + snap.avgLinkUtilization) / 2.0);
    }

    private void addPoint(XYChart.Series<Number, Number> series, double value) {
        series.getData().add(new XYChart.Data<>(sampleIndex, value));
        if (series.getData().size() > MAX_POINTS) {
            series.getData().remove(0);
        }
    }

    public void clear() {
        sampleIndex = 0;
        throughputSeries.getData().clear();
        latencySeries.getData().clear();
        pdrSeries.getData().clear();
        utilSeries.getData().clear();
    }
}
