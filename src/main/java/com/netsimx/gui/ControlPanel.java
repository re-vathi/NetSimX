package com.netsimx.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Module 12 - Interactive Dashboard: houses all the simulation controls
 * (start/pause/step/reset, algorithm selection, tick speed, traffic/failure
 * injection, topology editing mode, load/save). Exposes its Controls as
 * public final fields (deliberately simple - this is a controls container,
 * not a component with its own behavior) so {@code NetSimXApp} can wire
 * event handlers directly without an extra layer of indirection.
 */
public class ControlPanel extends VBox {

    public final ToggleButton runPauseButton = new ToggleButton("▶ Start");
    public final Button stepButton = new Button("Step");
    public final Button resetButton = new Button("Reset");

    public final ComboBox<String> algorithmCombo = new ComboBox<>();
    public final Slider speedSlider = new Slider(20, 1000, 100);

    public final ToggleButton addRouterModeButton = new ToggleButton("+ Router");
    public final ToggleButton addLinkModeButton = new ToggleButton("+ Link");
    public final Button removeSelectedButton = new Button("Remove Selected");

    public final Button loadTopologyButton = new Button("Load JSON…");
    public final Button saveTopologyButton = new Button("Save JSON…");
    public final Button exportStatsButton = new Button("Export Stats CSV…");

    public final ComboBox<String> trafficTypeCombo = new ComboBox<>();
    public final TextField trafficSourceField = new TextField();
    public final TextField trafficDestField = new TextField();
    public final Button addFlowButton = new Button("Add Flow");
    public final Button randomFlowButton = new Button("Add Random Flow");
    public final Button clearFlowsButton = new Button("Clear Flows");
    public final ListView<String> flowsList = new ListView<>();

    public final CheckBox chaosModeCheck = new CheckBox("Chaos mode (random failures)");
    public final Slider chaosSlider = new Slider(0, 0.1, 0.0);

    public final CheckBox aiTrainingCheck = new CheckBox("Train AI optimizer continuously");

    public final Label simTimeLabel = new Label("t = 0 ms");
    public final Label generatedLabel = new Label("Generated: 0");
    public final Label deliveredLabel = new Label("Delivered: 0");
    public final Label droppedLabel = new Label("Dropped: 0");
    public final Label pdrLabel = new Label("PDR: 100%");
    public final Label throughputLabel = new Label("Throughput: 0 pkt/s");

    public ControlPanel() {
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefWidth(300);
        setStyle("-fx-background-color: #161c28;");

        algorithmCombo.getItems().addAll("Dijkstra (OSPF)", "Bellman-Ford (RIP)", "ECMP", "AI Route Optimizer (Q-Learning)");
        algorithmCombo.getSelectionModel().selectFirst();

        trafficTypeCombo.getItems().addAll("VOICE", "VIDEO", "WEB", "EMAIL", "FILE_TRANSFER");
        trafficTypeCombo.getSelectionModel().selectFirst();
        trafficSourceField.setPromptText("source id (e.g. R1)");
        trafficDestField.setPromptText("dest id (e.g. R3)");

        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(false);
        Label speedLabel = new Label("Tick interval (ms):");

        chaosSlider.setShowTickLabels(false);
        chaosSlider.setDisable(true);
        chaosModeCheck.selectedProperty().addListener((obs, o, n) -> chaosSlider.setDisable(!n));

        HBox runControls = new HBox(6, runPauseButton, stepButton, resetButton);

        HBox editControls = new HBox(6, addRouterModeButton, addLinkModeButton);
        VBox editBox = new VBox(6, sectionLabel("Topology Editing"), editControls, removeSelectedButton,
                new Separator(), loadTopologyButton, saveTopologyButton, exportStatsButton);

        VBox routingBox = new VBox(6, sectionLabel("Routing Algorithm"), algorithmCombo,
                speedLabel, speedSlider);

        VBox trafficBox = new VBox(6, sectionLabel("Traffic Generator"),
                new HBox(6, new Label("Type:"), trafficTypeCombo),
                new HBox(6, trafficSourceField, trafficDestField),
                new HBox(6, addFlowButton, randomFlowButton, clearFlowsButton),
                flowsList);
        flowsList.setPrefHeight(90);

        VBox failureBox = new VBox(6, sectionLabel("Failure Simulation (right-click a router/link)"),
                chaosModeCheck, chaosSlider);

        VBox aiBox = new VBox(6, sectionLabel("AI Route Optimization"), aiTrainingCheck);

        VBox statsBox = new VBox(4, sectionLabel("Live Stats"), simTimeLabel, generatedLabel,
                deliveredLabel, droppedLabel, pdrLabel, throughputLabel);
        statsBox.setStyle("-fx-text-fill: #d6e4f0;");

        getChildren().addAll(runControls, new Separator(), routingBox, new Separator(),
                editBox, new Separator(), trafficBox, new Separator(), failureBox,
                new Separator(), aiBox, new Separator(), statsBox);

        for (Label l : new Label[]{simTimeLabel, generatedLabel, deliveredLabel, droppedLabel, pdrLabel, throughputLabel}) {
            l.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 11px;");
        }
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8fb4d6; -fx-font-weight: bold; -fx-font-size: 11px;");
        return l;
    }
}
