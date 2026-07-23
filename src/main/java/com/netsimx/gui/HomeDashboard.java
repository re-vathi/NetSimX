package com.netsimx.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * The "Home Dashboard" (Module 12 wireframe) - a lightweight project
 * launcher shown after the splash screen: a simple menu bar, a list of
 * recently opened topology files, and a grid of quick actions. This is
 * intentionally a thinner menu bar than the wireframe's full
 * File/Edit/View/Simulation/Tools/Help set - only the items that map to
 * something this app actually does are included, rather than padding out
 * empty dropdown menus for the sake of matching a wireframe exactly.
 */
public class HomeDashboard extends BorderPane {

    public HomeDashboard(List<String> recentProjectPaths,
                          Consumer<String> onOpenRecent,
                          Runnable onNewSimulation,
                          Runnable onImportJson,
                          Runnable onBenchmarkMode,
                          Runnable onOpenSampleTopologies,
                          Runnable onExit) {
        setStyle("-fx-background-color: #0b0f18;");

        setTop(buildMenuBar(onNewSimulation, onImportJson, onExit));
        setCenter(buildBody(recentProjectPaths, onOpenRecent, onNewSimulation, onImportJson, onBenchmarkMode, onOpenSampleTopologies));
    }

    private MenuBar buildMenuBar(Runnable onNewSimulation, Runnable onImportJson, Runnable onExit) {
        Menu file = new Menu("File");
        MenuItem newSim = new MenuItem("New Simulation");
        newSim.setOnAction(e -> onNewSimulation.run());
        MenuItem importJson = new MenuItem("Import JSON...");
        importJson.setOnAction(e -> onImportJson.run());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> onExit.run());
        file.getItems().addAll(newSim, importJson, new SeparatorMenuItem(), exit);

        Menu help = new Menu("Help");
        MenuItem about = new MenuItem("About NetSimX");
        about.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About NetSimX");
            alert.setHeaderText("NetSimX \u2014 Intelligent Network Routing & Traffic Simulator");
            alert.setContentText("A Java + JavaFX network simulator: routing algorithms, congestion, " +
                    "QoS, TCP/UDP, failures, live analytics, and an AI route optimizer.\n\nv1.0.0");
            alert.showAndWait();
        });
        help.getItems().add(about);

        MenuBar bar = new MenuBar(file, help);
        bar.setStyle("-fx-background-color: #10141c;");
        return bar;
    }

    private VBox buildBody(List<String> recentProjectPaths, Consumer<String> onOpenRecent,
                            Runnable onNewSimulation, Runnable onImportJson,
                            Runnable onBenchmarkMode, Runnable onOpenSampleTopologies) {
        VBox body = new VBox(24);
        body.setPadding(new Insets(30, 50, 30, 50));

        Label heading = new Label("Welcome to NetSimX");
        heading.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 26px; -fx-font-weight: bold;");

        HBox columns = new HBox(30);
        VBox.setVgrow(columns, Priority.ALWAYS);

        // --- Recent Projects column ---
        VBox recentBox = new VBox(10);
        recentBox.setPrefWidth(360);
        Label recentLabel = sectionLabel("Recent Projects");
        ListView<String> recentList = new ListView<>();
        recentList.setStyle("-fx-control-inner-background: #10141c; -fx-text-fill: #d6e4f0;");
        recentList.setPrefHeight(320);
        if (recentProjectPaths.isEmpty()) {
            recentList.setPlaceholder(new Label("No recent projects yet."));
        } else {
            recentList.getItems().addAll(recentProjectPaths);
        }
        recentList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = recentList.getSelectionModel().getSelectedItem();
                if (selected != null) onOpenRecent.accept(selected);
            }
        });
        Label hint = new Label("Double-click to open");
        hint.setStyle("-fx-text-fill: #556077; -fx-font-size: 10px; -fx-font-style: italic;");
        recentBox.getChildren().addAll(recentLabel, recentList, hint);

        // --- Quick Actions column ---
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(320);
        Label actionsLabel = sectionLabel("Quick Actions");

        Button newSimBtn = quickActionButton("+  New Simulation", "Start from a topology template");
        newSimBtn.setOnAction(e -> onNewSimulation.run());

        Button importBtn = quickActionButton("+  Import JSON", "Load a saved topology file");
        importBtn.setOnAction(e -> onImportJson.run());

        Button benchmarkBtn = quickActionButton("+  Benchmark Algorithms", "Compare routing algorithms head-to-head");
        benchmarkBtn.setOnAction(e -> onBenchmarkMode.run());

        Button samplesBtn = quickActionButton("+  Open Sample Topologies", "Campus LAN, Enterprise, Data Center, ISP, Smart City");
        samplesBtn.setOnAction(e -> onOpenSampleTopologies.run());

        actionsBox.getChildren().addAll(actionsLabel, newSimBtn, importBtn, benchmarkBtn, samplesBtn);

        columns.getChildren().addAll(recentBox, actionsBox);
        body.getChildren().addAll(heading, columns);
        return body;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8fb4d6; -fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    private Button quickActionButton(String title, String subtitle) {
        VBox content = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label subLabel = new Label(subtitle);
        subLabel.setStyle("-fx-text-fill: #7d93ad; -fx-font-size: 10.5px;");
        content.getChildren().addAll(titleLabel, subLabel);

        Button b = new Button();
        b.setGraphic(content);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color: #161c28; -fx-background-radius: 8; -fx-border-color: #2a3650; " +
                "-fx-border-radius: 8; -fx-padding: 14 16; -fx-cursor: hand;");
        return b;
    }
}
