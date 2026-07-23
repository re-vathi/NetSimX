package com.netsimx.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * The app's entry screen (Module 12 wireframe: "Splash Screen"). Purely a
 * navigation launchpad - no simulation state lives here. Four actions hand
 * off to {@link NetSimXApp}'s navigation methods.
 */
public class SplashScreen extends VBox {

    public SplashScreen(Runnable onNewSimulation, Runnable onOpenProject,
                         Runnable onBenchmarkMode, Runnable onDocumentation) {
        setAlignment(Pos.CENTER);
        setSpacing(18);
        setPadding(new Insets(40));
        setStyle("-fx-background-color: #0b0f18;");

        var logoUrl = getClass().getResource("/com/netsimx/gui/logo-wordmark.png");
        if (logoUrl != null) {
            ImageView logo = new ImageView(new Image(logoUrl.toExternalForm()));
            logo.setFitWidth(420);
            logo.setPreserveRatio(true);
            getChildren().add(logo);
        } else {
            Label title = new Label("NetSimX");
            title.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 42px; -fx-font-weight: bold;");
            getChildren().add(title);
        }

        Label tagline = new Label("Intelligent Network Routing & Traffic Simulator");
        tagline.setStyle("-fx-text-fill: #7d93ad; -fx-font-size: 15px;");
        getChildren().add(tagline);

        VBox buttonBox = new VBox(12);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(30, 0, 0, 0));
        buttonBox.setMaxWidth(260);

        Button newSim = primaryButton("New Simulation");
        newSim.setOnAction(e -> onNewSimulation.run());

        Button openProject = secondaryButton("Open Project");
        openProject.setOnAction(e -> onOpenProject.run());

        Button benchmark = secondaryButton("Benchmark Mode");
        benchmark.setOnAction(e -> onBenchmarkMode.run());

        Button docs = secondaryButton("Documentation");
        docs.setOnAction(e -> onDocumentation.run());

        for (Button b : new Button[]{newSim, openProject, benchmark, docs}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        buttonBox.getChildren().addAll(newSim, openProject, benchmark, docs);
        getChildren().add(buttonBox);

        Label version = new Label("v1.0.0");
        version.setStyle("-fx-text-fill: #3d4a63; -fx-font-size: 11px;");
        VBox.setMargin(version, new Insets(40, 0, 0, 0));
        getChildren().add(version);
    }

    private Button primaryButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #3d6fb4; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-padding: 12 24; -fx-background-radius: 6; -fx-cursor: hand;");
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1b2436; -fx-text-fill: #d6e4f0; -fx-font-size: 13px; " +
                "-fx-padding: 10 24; -fx-background-radius: 6; -fx-border-color: #2a3650; -fx-border-radius: 6; -fx-cursor: hand;");
        return b;
    }
}
