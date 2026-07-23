package com.netsimx.gui;

import com.netsimx.topology.TopologyGenerator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * "New Simulation Wizard" (Module 12 wireframe) - asks the user for a
 * simulation name, a topology template, a routing algorithm, and a
 * traffic preset, instead of dropping them into an empty canvas and
 * expecting them to build a network by hand. Hands the finished
 * {@link WizardResult} to a callback; all the actual topology/engine
 * construction happens in {@code NetSimXApp}, this class is purely the form.
 */
public class NewSimulationWizard extends VBox {

    /** Traffic presets offered by the wizard - each maps to a small starter set of flows. */
    public enum TrafficPreset { RANDOM, VIDEO_HEAVY, VOICE_HEAVY, HTTP_HEAVY }

    public record WizardResult(String name, TopologyGenerator.Template template, int routerCount,
                                String algorithmName, TrafficPreset trafficPreset) {}

    private final TextField nameField = new TextField("My Simulation");
    private final ToggleGroup topologyGroup = new ToggleGroup();
    private final ToggleGroup algorithmGroup = new ToggleGroup();
    private final ToggleGroup trafficGroup = new ToggleGroup();
    private final Spinner<Integer> routerCountSpinner = new Spinner<>(2, 20, 9);

    public NewSimulationWizard(Runnable onCancel, java.util.function.Consumer<WizardResult> onCreate) {
        setSpacing(20);
        setPadding(new Insets(36, 60, 36, 60));
        setStyle("-fx-background-color: #0b0f18;");

        Label heading = new Label("New Simulation");
        heading.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 24px; -fx-font-weight: bold;");

        VBox nameSection = section("Simulation Name",
                nameField);
        nameField.setStyle(fieldStyle());
        nameField.setMaxWidth(320);

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(8);

        grid.add(radioColumn("Topology", topologyGroup,
                "Empty", "Star", "Mesh", "Ring", "Tree", "ISP Backbone"), 0, 0);
        grid.add(radioColumn("Algorithm", algorithmGroup,
                "Dijkstra", "Bellman-Ford", "ECMP", "AI Route Optimizer"), 1, 0);
        grid.add(radioColumn("Traffic", trafficGroup,
                "Random Mixed", "Video-Heavy", "Voice-Heavy", "HTTP/Web-Heavy"), 2, 0);

        selectFirst(topologyGroup);
        selectFirst(algorithmGroup);
        selectFirst(trafficGroup);

        HBox routerCountBox = new HBox(10, new Label("Router count (for generated templates):"), routerCountSpinner);
        routerCountBox.setAlignment(Pos.CENTER_LEFT);
        styleLabel((Label) routerCountBox.getChildren().get(0));
        routerCountSpinner.setEditable(true);
        routerCountSpinner.setPrefWidth(80);

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(20, 0, 0, 0));

        Button cancel = new Button("Cancel");
        cancel.setStyle("-fx-background-color: #1b2436; -fx-text-fill: #d6e4f0; -fx-padding: 10 22; -fx-background-radius: 6; -fx-cursor: hand;");
        cancel.setOnAction(e -> onCancel.run());

        Button create = new Button("Create");
        create.setStyle("-fx-background-color: #3d6fb4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 26; -fx-background-radius: 6; -fx-cursor: hand;");
        create.setOnAction(e -> {
            String name = nameField.getText().isBlank() ? "Untitled Simulation" : nameField.getText().trim();
            TopologyGenerator.Template template = mapTopologyChoice(selectedText(topologyGroup));
            String algorithmName = mapAlgorithmChoice(selectedText(algorithmGroup));
            TrafficPreset preset = mapTrafficChoice(selectedText(trafficGroup));
            onCreate.accept(new WizardResult(name, template, routerCountSpinner.getValue(), algorithmName, preset));
        });

        buttons.getChildren().addAll(cancel, create);

        getChildren().addAll(heading, nameSection, grid, routerCountBox, buttons);
    }

    private VBox section(String title, javafx.scene.Node content) {
        Label label = sectionLabel(title);
        VBox box = new VBox(8, label, content);
        return box;
    }

    private VBox radioColumn(String title, ToggleGroup group, String... options) {
        VBox box = new VBox(8);
        box.getChildren().add(sectionLabel(title));
        for (String opt : options) {
            RadioButton rb = new RadioButton(opt);
            rb.setToggleGroup(group);
            rb.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 12.5px;");
            box.getChildren().add(rb);
        }
        return box;
    }

    private void selectFirst(ToggleGroup group) {
        if (!group.getToggles().isEmpty()) group.selectToggle(group.getToggles().get(0));
    }

    private String selectedText(ToggleGroup group) {
        Toggle t = group.getSelectedToggle();
        return t instanceof RadioButton rb ? rb.getText() : "";
    }

    private TopologyGenerator.Template mapTopologyChoice(String text) {
        return switch (text) {
            case "Star" -> TopologyGenerator.Template.STAR;
            case "Mesh" -> TopologyGenerator.Template.MESH;
            case "Ring" -> TopologyGenerator.Template.RING;
            case "Tree" -> TopologyGenerator.Template.TREE;
            case "ISP Backbone" -> TopologyGenerator.Template.ISP_BACKBONE;
            default -> TopologyGenerator.Template.EMPTY;
        };
    }

    private String mapAlgorithmChoice(String text) {
        return switch (text) {
            case "Bellman-Ford" -> "Bellman-Ford (RIP)";
            case "ECMP" -> "ECMP";
            case "AI Route Optimizer" -> "AI Route Optimizer (Q-Learning)";
            default -> "Dijkstra (OSPF)";
        };
    }

    private TrafficPreset mapTrafficChoice(String text) {
        return switch (text) {
            case "Video-Heavy" -> TrafficPreset.VIDEO_HEAVY;
            case "Voice-Heavy" -> TrafficPreset.VOICE_HEAVY;
            case "HTTP/Web-Heavy" -> TrafficPreset.HTTP_HEAVY;
            default -> TrafficPreset.RANDOM;
        };
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8fb4d6; -fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    private void styleLabel(Label l) {
        l.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 12.5px;");
    }

    private String fieldStyle() {
        return "-fx-background-color: #10141c; -fx-text-fill: #d6e4f0; -fx-border-color: #2a3650; -fx-padding: 6 10;";
    }
}
