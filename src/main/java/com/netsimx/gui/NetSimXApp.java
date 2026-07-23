package com.netsimx.gui;

import com.netsimx.ai.QLearningRouteOptimizer;
import com.netsimx.analytics.PerformanceSnapshot;
import com.netsimx.model.*;
import com.netsimx.persistence.CsvExporter;
import com.netsimx.persistence.TopologyIO;
import com.netsimx.routing.BellmanFordRouting;
import com.netsimx.routing.DijkstraRouting;
import com.netsimx.routing.ECMPRouting;
import com.netsimx.routing.RoutingAlgorithm;
import com.netsimx.simulation.SimulationEngine;
import com.netsimx.simulation.SimulationListener;
import com.netsimx.simulation.TrafficGenerator;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Module 12 - Interactive Dashboard entry point. Wires the headless
 * {@link SimulationEngine} to the JavaFX controls/canvas/charts and owns
 * the two clocks that drive the UI: a fixed-rate {@link Timeline} that
 * steps the simulation, and a free-running {@link AnimationTimer} that
 * smoothly interpolates in-flight packet dots between simulation ticks.
 */
public class NetSimXApp extends Application {

    private NetworkTopology topology;
    private SimulationEngine engine;

    private TopologyCanvas canvas;
    private ChartsPanel chartsPanel;
    private LogConsole logConsole;
    private ControlPanel controls;
    private NetworkPanel networkPanel;
    private PacketInspectorPanel packetInspectorPanel;
    private RoutingTablePanel routingTablePanel;
    private TabPane rightTabs;
    private Tab networkTab;
    private Tab packetInspectorTab;
    private Tab routingTableTab;

    private final Random uiRandom = new Random();

    private Timeline simTimeline;
    private AnimationTimer animationTimer;
    private long lastFrameNanos = -1;

    private final QLearningRouteOptimizer aiOptimizer = new QLearningRouteOptimizer();
    private Router pendingLinkSource = null;

    private Stage stage;
    private Scene scene;
    private String currentTopologyName = "Untitled Simulation";
    private BorderPane workspaceRoot;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        var visualBounds = Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.min(1400, visualBounds.getWidth() * 0.95);
        double windowHeight = Math.min(820, visualBounds.getHeight() * 0.9);

        scene = new Scene(new javafx.scene.layout.StackPane(), windowWidth, windowHeight);
        var cssUrl = getClass().getResource("/com/netsimx/gui/dashboard.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("NetSimX \u2014 Intelligent Network Routing & Traffic Simulator");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        stage.setOnCloseRequest(e -> {
            if (simTimeline != null) simTimeline.stop();
            if (animationTimer != null) animationTimer.stop();
        });

        if (Boolean.getBoolean("netsimx.demo")) {
            // Demo/GIF-recording mode bypasses the splash/dashboard/wizard flow
            // entirely and drops straight into the workspace, preserving the
            // exact automated screenshot/recording harness used for the README.
            enterWorkspace(loadInitialTopology(), "Campus LAN (sample)", null, null);
            runDemoSequence();
        } else {
            showSplash();
        }
    }

    // ------------------------------------------------------------------ //
    // Navigation
    // ------------------------------------------------------------------ //

    private void showSplash() {
        SplashScreen splash = new SplashScreen(
                this::showWizard,
                this::openProjectFileChooser,
                this::showBenchmarkScreen,
                this::showDocumentation);
        scene.setRoot(splash);
    }

    private void showDashboard() {
        HomeDashboard dashboard = new HomeDashboard(
                com.netsimx.persistence.RecentProjects.load(),
                this::openRecentProject,
                this::showWizard,
                this::openProjectFileChooser,
                this::showBenchmarkScreen,
                this::openSampleTopologiesDialog,
                () -> { if (simTimeline != null) simTimeline.stop(); if (animationTimer != null) animationTimer.stop(); stage.close(); });
        scene.setRoot(dashboard);
    }

    private void showWizard() {
        NewSimulationWizard wizard = new NewSimulationWizard(this::showSplash, this::onWizardComplete);
        scene.setRoot(wizard);
    }

    private void showBenchmarkScreen() {
        BenchmarkScreen benchmarkScreen = new BenchmarkScreen(this::returnFromSideScreen);
        scene.setRoot(benchmarkScreen);
    }

    private void showReportScreen() {
        if (engine == null) return; // no workspace session active
        ReportScreen report = new ReportScreen(engine, currentTopologyName, this::returnFromSideScreen);
        scene.setRoot(report);
    }

    private void showDocumentation() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("NetSimX Documentation");
        alert.setHeaderText("NetSimX \u2014 Intelligent Network Routing & Traffic Simulator");
        alert.setContentText(
                "Quick reference lives in this project's README.md.\n\n" +
                "For the complete story - why the project exists, a beginner-friendly\n" +
                "walkthrough of every file, the full development history, and a guide\n" +
                "to extending it - see docs/PROJECT_DOCUMENTATION.md.\n\n" +
                "\u2022 Module-by-module architecture overview\n" +
                "\u2022 How the simulation loop works (traffic, QoS, congestion, TCP/UDP, failures)\n" +
                "\u2022 Every bug found and fixed, and why\n" +
                "\u2022 How to extend NetSimX with new algorithms, traffic types, or topologies");
        alert.showAndWait();
    }

    /** "Back" from a side screen (Benchmark/Report) returns to the workspace if one is active, otherwise the splash screen. */
    private void returnFromSideScreen() {
        if (workspaceRoot != null) {
            scene.setRoot(workspaceRoot);
        } else {
            showSplash();
        }
    }

    private void openRecentProject(String path) {
        try {
            NetworkTopology loaded = TopologyIO.load(Path.of(path));
            com.netsimx.persistence.RecentProjects.addAndSave(path);
            String name = Path.of(path).getFileName().toString();
            enterWorkspace(loaded, name, null, null);
        } catch (IOException | RuntimeException ex) {
            showError("Failed to open project", ex.getMessage());
        }
    }

    private void openProjectFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Network Topology (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            NetworkTopology loaded = TopologyIO.load(file.toPath());
            com.netsimx.persistence.RecentProjects.addAndSave(file.getAbsolutePath());
            enterWorkspace(loaded, file.getName(), null, null);
        } catch (IOException | RuntimeException ex) {
            showError("Failed to open project", ex.getMessage());
        }
    }

    private void openSampleTopologiesDialog() {
        Path samplesDir = Path.of("config", "samples");
        List<String> names = new ArrayList<>();
        Map<String, Path> pathsByName = new java.util.LinkedHashMap<>();
        try (var stream = Files.list(samplesDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                String label = p.getFileName().toString().replace(".json", "").replace("-", " ");
                names.add(label);
                pathsByName.put(label, p);
            });
        } catch (IOException e) {
            showError("Sample topologies not found", "Could not read " + samplesDir.toAbsolutePath());
            return;
        }
        if (names.isEmpty()) {
            showError("No sample topologies found", samplesDir.toAbsolutePath().toString());
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Open Sample Topology");
        dialog.setHeaderText("Choose a sample network to load");
        dialog.showAndWait().ifPresent(choice -> {
            try {
                NetworkTopology loaded = TopologyIO.load(pathsByName.get(choice));
                enterWorkspace(loaded, choice, null, null);
            } catch (IOException ex) {
                showError("Failed to load sample", ex.getMessage());
            }
        });
    }

    private void onWizardComplete(NewSimulationWizard.WizardResult result) {
        NetworkTopology generated = com.netsimx.topology.TopologyGenerator.generate(result.template(), result.routerCount());
        enterWorkspace(generated, result.name(), result.trafficPreset(), result.algorithmName());
    }

    /**
     * Opt-in scripted sequence (enable with {@code -Dnetsimx.demo=true}) used
     * only to record deterministic screenshots/GIFs for the README - starts
     * the simulation immediately, adds a couple of extra flows for a livelier
     * visual, and fails a central link a few seconds in so a recording
     * captures a real reroute event without needing manual clicking.
     */
    private void runDemoSequence() {
        controls.runPauseButton.setSelected(true);

        if (topology.getRouter("R1") != null && topology.getRouter("R9") != null) {
            engine.getTrafficGenerator().addFlow(
                    new TrafficGenerator.Flow("R1", "R9", TrafficGenerator.TrafficType.VIDEO, 0.3));
            controls.flowsList.getItems().add("R1 -> R9 [VIDEO]");
        }
        if (topology.getRouter("R7") != null && topology.getRouter("R3") != null) {
            engine.getTrafficGenerator().addFlow(
                    new TrafficGenerator.Flow("R7", "R3", TrafficGenerator.TrafficType.FILE_TRANSFER, 0.25));
            controls.flowsList.getItems().add("R7 -> R3 [FILE_TRANSFER]");
        }

        Thread failureTimer = new Thread(() -> {
            try { Thread.sleep(4000); } catch (InterruptedException ignored) { return; }
            Platform.runLater(() -> {
                Link l = topology.getLink("L6"); // Dist-1 <-> Dist-2 in sample-network.json
                if (l != null) {
                    engine.setLinkUp(l, false);
                    logConsole.append("[demo] Failing L6 (Dist-1<->Dist-2) to show live reroute.");
                }
            });
        });
        failureTimer.setDaemon(true);
        failureTimer.start();
    }

    // ------------------------------------------------------------------ //
    // Layout
    // ------------------------------------------------------------------ //

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();
        root.setTop(buildWorkspaceTopBar());

        Pane canvasHost = new Pane(canvas);
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        ScrollPane canvasScroll = new ScrollPane(canvasHost);
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);

        ScrollPane chartsScroll = new ScrollPane(chartsPanel);
        chartsScroll.setFitToWidth(true);

        ScrollPane networkScroll = new ScrollPane(networkPanel);
        networkScroll.setFitToWidth(true);
        ScrollPane packetScroll = new ScrollPane(packetInspectorPanel);
        packetScroll.setFitToWidth(true);
        ScrollPane routingScroll = new ScrollPane(routingTablePanel);
        routingScroll.setFitToWidth(true);

        networkTab = new Tab("Network", networkScroll);
        networkTab.setClosable(false);
        packetInspectorTab = new Tab("Packet Inspector", packetScroll);
        packetInspectorTab.setClosable(false);
        routingTableTab = new Tab("Routing Table", routingScroll);
        routingTableTab.setClosable(false);
        Tab chartsTab = new Tab("Live Charts", chartsScroll);
        chartsTab.setClosable(false);
        Tab logTab = new Tab("Event Log", logConsole);
        logTab.setClosable(false);
        rightTabs = new TabPane(networkTab, packetInspectorTab, routingTableTab, chartsTab, logTab);
        rightTabs.setPrefWidth(380);

        SplitPane split = new SplitPane(controls, canvasScroll, rightTabs);
        split.setDividerPositions(0.19, 0.75);

        root.setCenter(split);
        return root;
    }

    private javafx.scene.layout.HBox buildWorkspaceTopBar() {
        Label title = new Label(currentTopologyName);
        title.setStyle("-fx-text-fill: #8fb4d6; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button home = navBarButton("\uD83C\uDFE0 Home");
        home.setOnAction(e -> showDashboard());
        Button benchmark = navBarButton("\uD83D\uDCCA Benchmark");
        benchmark.setOnAction(e -> showBenchmarkScreen());
        Button report = navBarButton("\uD83D\uDCC4 Report");
        report.setOnAction(e -> showReportScreen());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(10, home, benchmark, report, spacer, title);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #10141c; -fx-border-color: transparent transparent #1e2635 transparent; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private Button navBarButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1b2436; -fx-text-fill: #d6e4f0; -fx-font-size: 11.5px; " +
                "-fx-padding: 6 14; -fx-background-radius: 5; -fx-cursor: hand;");
        return b;
    }

    // ------------------------------------------------------------------ //
    // Initial topology
    // ------------------------------------------------------------------ //

    private NetworkTopology loadInitialTopology() {
        try {
            return TopologyIO.load(Path.of("config", "sample-network.json"));
        } catch (IOException e) {
            NetworkTopology fallback = new NetworkTopology();
            fallback.addRouter(new Router("R1", "R1", 150, 150, 48));
            fallback.addRouter(new Router("R2", "R2", 400, 150, 48));
            fallback.addRouter(new Router("R3", "R3", 650, 150, 48));
            fallback.addRouter(new Router("R4", "R4", 400, 350, 48));
            fallback.addLink(new Link("L1", "R1", "R2", 1, 5, 100));
            fallback.addLink(new Link("L2", "R2", "R3", 1, 5, 100));
            fallback.addLink(new Link("L3", "R1", "R4", 2, 8, 80));
            fallback.addLink(new Link("L4", "R4", "R3", 2, 8, 80));
            return fallback;
        }
    }

    /**
     * Tears down any previous workspace session (stopping its clocks so they
     * don't keep ticking in the background) and builds a brand new one
     * around {@code initialTopology}. This is the single entry point every
     * navigation path funnels through: the wizard, "Open Project", "Open
     * Sample Topology", a recent-project double-click, and demo mode.
     */
    private void enterWorkspace(NetworkTopology initialTopology, String topologyName,
                                 NewSimulationWizard.TrafficPreset trafficPreset, String algorithmName) {
        if (simTimeline != null) simTimeline.stop();
        if (animationTimer != null) animationTimer.stop();
        lastFrameNanos = -1;

        topology = initialTopology;
        currentTopologyName = topologyName;
        engine = new SimulationEngine(topology);

        canvas = new TopologyCanvas(900, 650);
        canvas.setTopology(topology);
        chartsPanel = new ChartsPanel();
        logConsole = new LogConsole();
        controls = new ControlPanel();
        networkPanel = new NetworkPanel();
        packetInspectorPanel = new PacketInspectorPanel();
        routingTablePanel = new RoutingTablePanel();

        wireEngineListeners();
        wireControlHandlers();
        wireCanvasHandlers();

        workspaceRoot = buildLayout();
        scene.setRoot(workspaceRoot);

        if (algorithmName != null) {
            engine.setRoutingAlgorithm(algorithmFor(algorithmName));
            controls.algorithmCombo.getSelectionModel().select(algorithmName);
        }
        engine.recomputeRoutes();
        if (trafficPreset != null) applyTrafficPreset(trafficPreset);

        startClocks();
        logConsole.append("Workspace ready: \"" + topologyName + "\" \u2014 " +
                topology.routerCount() + " routers, " + topology.linkCount() + " links.");
    }

    /** Seeds a small starter set of traffic flows matching the wizard's chosen preset. */
    private void applyTrafficPreset(NewSimulationWizard.TrafficPreset preset) {
        List<Router> up = topology.getRouters().stream().filter(Router::isUp).toList();
        if (up.size() < 2) return;

        TrafficGenerator.TrafficType primaryType = switch (preset) {
            case VIDEO_HEAVY -> TrafficGenerator.TrafficType.VIDEO;
            case VOICE_HEAVY -> TrafficGenerator.TrafficType.VOICE;
            case HTTP_HEAVY -> TrafficGenerator.TrafficType.WEB;
            case RANDOM -> null; // handled by addRandomFlow's own weighted mix
        };

        int flowCount = Math.min(3, up.size() - 1);
        for (int i = 0; i < flowCount; i++) {
            if (primaryType == null) {
                var flow = engine.getTrafficGenerator().addRandomFlow(topology);
                if (flow != null) controls.flowsList.getItems().add(
                        String.format("%s -> %s [%s]", flow.sourceId, flow.destinationId, flow.type));
            } else {
                Router src = up.get(uiRandom.nextInt(up.size()));
                Router dst;
                do { dst = up.get(uiRandom.nextInt(up.size())); } while (dst == src);
                var flow = new TrafficGenerator.Flow(src.getId(), dst.getId(), primaryType, 0.25);
                engine.getTrafficGenerator().addFlow(flow);
                controls.flowsList.getItems().add(String.format("%s -> %s [%s]", src.getId(), dst.getId(), primaryType));
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Engine <-> GUI event wiring
    // ------------------------------------------------------------------ //

    private void wireEngineListeners() {
        engine.addListener(new SimulationListener() {
            @Override
            public void onPacketHop(Packet packet, String fromRouterId, String toRouterId, Link viaLink) {
                canvas.onPacketHop(packet, fromRouterId, toRouterId);
            }

            @Override
            public void onPacketDelivered(Packet packet) {
                canvas.onPacketFinished(packet);
            }

            @Override
            public void onPacketDropped(Packet packet, String reason) {
                canvas.onPacketFinished(packet);
                logConsole.append(String.format("DROP #%d [%s->%s] %s: %s",
                        packet.getId(), packet.getSourceId(), packet.getDestinationId(), packet.getProtocol(), reason));
            }

            @Override
            public void onLog(String message) {
                logConsole.append(message);
            }

            @Override
            public void onLinkStatusChanged(Link link, boolean up) {
                canvas.redraw();
            }

            @Override
            public void onRouterStatusChanged(Router router, boolean up) {
                canvas.redraw();
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Control panel event wiring
    // ------------------------------------------------------------------ //

    private void wireControlHandlers() {
        controls.runPauseButton.selectedProperty().addListener((obs, was, isNowRunning) -> {
            controls.runPauseButton.setText(isNowRunning ? "\u23F8 Pause" : "\u25B6 Start");
            if (isNowRunning) simTimeline.play(); else simTimeline.pause();
        });

        controls.stepButton.setOnAction(e -> {
            if (!controls.runPauseButton.isSelected()) {
                doTickAndRefresh();
            }
        });

        controls.resetButton.setOnAction(e -> {
            engine.reset();
            chartsPanel.clear();
            canvas.clearSelection();
            packetInspectorPanel.show(null);
            canvas.redraw();
            updateStatLabels();
            logConsole.append("Simulation reset.");
        });

        controls.algorithmCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            engine.setRoutingAlgorithm(algorithmFor(newV));
            logConsole.append("Routing algorithm switched to: " + newV);
        });

        controls.speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            long newInterval = Math.round(newV.doubleValue());
            engine.setTickIntervalMs(newInterval);
            rebuildTimeline();
        });

        controls.addRouterModeButton.selectedProperty().addListener((obs, o, isSelected) -> {
            if (isSelected) controls.addLinkModeButton.setSelected(false);
        });
        controls.addLinkModeButton.selectedProperty().addListener((obs, o, isSelected) -> {
            if (isSelected) controls.addRouterModeButton.setSelected(false);
            pendingLinkSource = null;
        });

        controls.removeSelectedButton.setOnAction(e -> {
            Router r = canvas.getSelectedRouter();
            Link l = canvas.getSelectedLink();
            if (r != null) {
                removeRouterAction(r);
            } else if (l != null) {
                removeLinkAction(l);
            }
        });

        controls.loadTopologyButton.setOnAction(e -> onLoadTopology());
        controls.saveTopologyButton.setOnAction(e -> onSaveTopology());
        controls.exportStatsButton.setOnAction(e -> onExportStats());

        controls.addFlowButton.setOnAction(e -> onAddFlow());
        controls.randomFlowButton.setOnAction(e -> onAddRandomFlow());
        controls.clearFlowsButton.setOnAction(e -> {
            engine.getTrafficGenerator().clearFlows();
            controls.flowsList.getItems().clear();
            logConsole.append("All traffic flows cleared.");
        });

        controls.chaosModeCheck.selectedProperty().addListener((obs, o, n) -> updateChaosSetting());
        controls.chaosSlider.valueProperty().addListener((obs, o, n) -> updateChaosSetting());
    }

    private RoutingAlgorithm algorithmFor(String name) {
        return switch (name) {
            case "Bellman-Ford (RIP)" -> new BellmanFordRouting();
            case "ECMP" -> new ECMPRouting();
            case "AI Route Optimizer (Q-Learning)" -> aiOptimizer;
            default -> new DijkstraRouting();
        };
    }

    private void updateChaosSetting() {
        double p = controls.chaosModeCheck.isSelected() ? controls.chaosSlider.getValue() : 0.0;
        engine.getFailureSimulator().setRandomFailureProbabilityPerTick(p);
    }

    private void onLoadTopology() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Network Topology (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = chooser.showOpenDialog(canvas.getScene().getWindow());
        if (file == null) return;

        try {
            NetworkTopology loaded = TopologyIO.load(file.toPath());
            topology.clear();
            for (Router r : loaded.getRouters()) topology.addRouter(r);
            for (Link l : loaded.getLinks()) topology.addLink(l);

            engine.reset();
            engine.getTrafficGenerator().clearFlows();
            controls.flowsList.getItems().clear();
            chartsPanel.clear();
            canvas.clearSelection();
            networkPanel.show(null, topology);
            routingTablePanel.show(null, topology, engine.getRoutingTable());
            packetInspectorPanel.show(null);
            canvas.redraw();
            updateStatLabels();
            logConsole.append("Loaded topology from " + file.getName() + " (" +
                    topology.routerCount() + " routers, " + topology.linkCount() + " links).");
        } catch (IOException | RuntimeException ex) {
            showError("Failed to load topology", ex.getMessage());
        }
    }

    private void onSaveTopology() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Network Topology (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("network-topology.json");
        var file = chooser.showSaveDialog(canvas.getScene().getWindow());
        if (file == null) return;

        try {
            TopologyIO.save(topology, file.toPath());
            logConsole.append("Saved topology to " + file.getName());
        } catch (IOException ex) {
            showError("Failed to save topology", ex.getMessage());
        }
    }

    private void onExportStats() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Performance Stats (CSV)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("netsimx-stats.csv");
        var file = chooser.showSaveDialog(canvas.getScene().getWindow());
        if (file == null) return;

        try {
            CsvExporter.export(engine.getStatistics().getHistory(), file.toPath());
            logConsole.append("Exported " + engine.getStatistics().getHistory().size() + " samples to " + file.getName());
        } catch (IOException ex) {
            showError("Failed to export stats", ex.getMessage());
        }
    }

    private void onAddFlow() {
        String src = controls.trafficSourceField.getText().trim();
        String dst = controls.trafficDestField.getText().trim();
        if (topology.getRouter(src) == null || topology.getRouter(dst) == null) {
            showError("Invalid flow", "Both source and destination router IDs must exist in the topology.");
            return;
        }
        if (src.equals(dst)) {
            showError("Invalid flow", "Source and destination must be different routers.");
            return;
        }
        TrafficGenerator.TrafficType type = TrafficGenerator.TrafficType.valueOf(
                controls.trafficTypeCombo.getSelectionModel().getSelectedItem());
        TrafficGenerator.Flow flow = new TrafficGenerator.Flow(src, dst, type, 0.2);
        engine.getTrafficGenerator().addFlow(flow);
        controls.flowsList.getItems().add(String.format("%s -> %s [%s]", src, dst, type));
        logConsole.append("Added traffic flow " + src + " -> " + dst + " (" + type + ")");
    }

    private void onAddRandomFlow() {
        TrafficGenerator.Flow flow = engine.getTrafficGenerator().addRandomFlow(topology);
        if (flow == null) {
            showError("Cannot add flow", "Need at least two active routers in the topology.");
            return;
        }
        controls.flowsList.getItems().add(String.format("%s -> %s [%s] (random)", flow.sourceId, flow.destinationId, flow.type));
        logConsole.append("Added random flow " + flow.sourceId + " -> " + flow.destinationId);
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("NetSimX");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------ //
    // Canvas topology-editing wiring (add router / add link modes)
    // ------------------------------------------------------------------ //

    private void wireCanvasHandlers() {
        canvas.setOnEmptyAreaClicked((x, y) -> {
            if (!controls.addRouterModeButton.isSelected()) return;
            String id = nextRouterId();
            Router router = new Router(id, id, x, y, 48);
            topology.addRouter(router);
            engine.recomputeRoutes();
            canvas.redraw();
            logConsole.append("Added router " + id + " at (" + Math.round(x) + "," + Math.round(y) + ")");
        });

        canvas.setOnRouterSelected(router -> {
            if (controls.addLinkModeButton.isSelected()) {
                if (pendingLinkSource == null) {
                    pendingLinkSource = router;
                    logConsole.append("Link mode: " + router.getId() + " selected as source \u2014 click a destination router.");
                } else if (pendingLinkSource != router) {
                    if (topology.findLinkBetween(pendingLinkSource.getId(), router.getId()).isEmpty()) {
                        String id = nextLinkId();
                        topology.addLink(new Link(id, pendingLinkSource.getId(), router.getId(), 1, 5, 100));
                        engine.recomputeRoutes();
                        logConsole.append("Added link " + id + ": " + pendingLinkSource.getId() + " <-> " + router.getId());
                    } else {
                        logConsole.append("Link already exists between " + pendingLinkSource.getId() + " and " + router.getId());
                    }
                    pendingLinkSource = null;
                    canvas.redraw();
                }
            } else {
                // Normal click (not building a link): feed the inspector panels so
                // whichever tab the user switches to shows this router's live data.
                networkPanel.show(router, topology);
                routingTablePanel.show(router, topology, engine.getRoutingTable());
            }
        });

        canvas.setOnPacketSelected(packet -> {
            packetInspectorPanel.show(packet, topology, engine.getSimTimeMs());
            rightTabs.getSelectionModel().select(packetInspectorTab);
        });

        canvas.setOnRouterContextMenu((router, mouseEvent) ->
                buildRouterContextMenu(router).show(canvas, mouseEvent.getScreenX(), mouseEvent.getScreenY()));
        canvas.setOnLinkContextMenu((link, mouseEvent) ->
                buildLinkContextMenu(link).show(canvas, mouseEvent.getScreenX(), mouseEvent.getScreenY()));
    }

    // ------------------------------------------------------------------ //
    // Context menus (right-click a router or link on the canvas)
    // ------------------------------------------------------------------ //

    private ContextMenu buildRouterContextMenu(Router router) {
        ContextMenu menu = new ContextMenu();

        MenuItem inspect = new MenuItem("Inspect");
        inspect.setOnAction(e -> {
            networkPanel.show(router, topology);
            rightTabs.getSelectionModel().select(networkTab);
        });

        MenuItem routingTableItem = new MenuItem("Routing Table");
        routingTableItem.setOnAction(e -> {
            routingTablePanel.show(router, topology, engine.getRoutingTable());
            rightTabs.getSelectionModel().select(routingTableTab);
        });

        MenuItem toggle = new MenuItem(router.isUp() ? "Disable Router" : "Enable Router");
        toggle.setOnAction(e -> {
            engine.setRouterUp(router, !router.isUp());
            canvas.redraw();
            if (networkPanel.getCurrent() == router) networkPanel.refresh(topology);
        });

        MenuItem generateTraffic = new MenuItem("Generate Traffic From Here");
        generateTraffic.setOnAction(e -> generateTrafficFrom(router));

        MenuItem rename = new MenuItem("Rename...");
        rename.setOnAction(e -> renameRouter(router));

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> removeRouterAction(router));

        menu.getItems().addAll(inspect, routingTableItem, new javafx.scene.control.SeparatorMenuItem(),
                toggle, generateTraffic, rename, new javafx.scene.control.SeparatorMenuItem(), delete);
        return menu;
    }

    private ContextMenu buildLinkContextMenu(Link link) {
        ContextMenu menu = new ContextMenu();

        MenuItem bandwidth = new MenuItem("Bandwidth...");
        bandwidth.setOnAction(e -> editLinkNumericProperty(link, "Bandwidth (packets/sec)",
                link.getBandwidthPps(), link::setBandwidthPps));

        MenuItem latency = new MenuItem("Latency...");
        latency.setOnAction(e -> editLinkNumericProperty(link, "Latency (ms)",
                link.getLatencyMs(), link::setLatencyMs));

        MenuItem packetLoss = new MenuItem("Packet Loss %...");
        packetLoss.setOnAction(e -> editLinkNumericProperty(link, "Packet loss probability (%)",
                link.getLossProbability() * 100, pct -> link.setLossProbability(pct / 100.0)));

        MenuItem toggle = new MenuItem(link.isUp() ? "Disable Link" : "Enable Link");
        toggle.setOnAction(e -> {
            engine.setLinkUp(link, !link.isUp());
            canvas.redraw();
        });

        MenuItem congest = new MenuItem(link.isCongested() ? "Release Congestion" : "Congest Link (10%)");
        // TODO: 10% is hardcoded here. Link.congest() already takes a fraction as
        // a parameter, so this could easily be a small dialog like the bandwidth/
        // latency/loss editors above - just didn't seem worth the extra click for
        // a demo feature. Would be a quick win if someone wants finer control.
        congest.setOnAction(e -> {
            if (link.isCongested()) link.releaseCongestion(); else link.congest(0.1);
            canvas.redraw();
            logConsole.append((link.isCongested() ? "Congested " : "Released congestion on ") + link.getId());
        });

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> removeLinkAction(link));

        menu.getItems().addAll(bandwidth, latency, packetLoss, new javafx.scene.control.SeparatorMenuItem(),
                toggle, congest, new javafx.scene.control.SeparatorMenuItem(), delete);
        return menu;
    }

    private void editLinkNumericProperty(Link link, String label, double currentValue, java.util.function.DoubleConsumer setter) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentValue));
        dialog.setTitle("Edit " + link.getId());
        dialog.setHeaderText(label);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(text -> {
            try {
                double value = Double.parseDouble(text.trim());
                setter.accept(value);
                canvas.redraw();
                if (networkPanel.getCurrent() != null) networkPanel.refresh(topology);
                logConsole.append("Updated " + link.getId() + ": " + label + " = " + value);
            } catch (NumberFormatException ex) {
                showError("Invalid value", "\"" + text + "\" isn't a valid number.");
            }
        });
    }

    private void renameRouter(Router router) {
        TextInputDialog dialog = new TextInputDialog(router.getLabel());
        dialog.setTitle("Rename " + router.getId());
        dialog.setHeaderText("New display label for " + router.getId());
        dialog.setContentText("Label:");
        dialog.showAndWait().ifPresent(newLabel -> {
            if (!newLabel.isBlank()) {
                router.setLabel(newLabel.trim());
                canvas.redraw();
                if (networkPanel.getCurrent() == router) networkPanel.refresh(topology);
                logConsole.append("Renamed " + router.getId() + " -> \"" + newLabel.trim() + "\"");
            }
        });
    }

    private void generateTrafficFrom(Router source) {
        List<Router> candidates = topology.getRouters().stream()
                .filter(r -> r.isUp() && r != source)
                .toList();
        if (candidates.isEmpty()) {
            showError("Cannot generate traffic", "No other active router to send traffic to.");
            return;
        }
        Router dest = candidates.get(uiRandom.nextInt(candidates.size()));
        TrafficGenerator.TrafficType[] types = TrafficGenerator.TrafficType.values();
        TrafficGenerator.TrafficType type = types[uiRandom.nextInt(types.length)];

        TrafficGenerator.Flow flow = new TrafficGenerator.Flow(source.getId(), dest.getId(), type, 0.25);
        engine.getTrafficGenerator().addFlow(flow);
        controls.flowsList.getItems().add(String.format("%s -> %s [%s] (from context menu)", source.getId(), dest.getId(), type));
        logConsole.append("Generated traffic flow " + source.getId() + " -> " + dest.getId() + " (" + type + ")");
    }

    private void removeRouterAction(Router router) {
        topology.removeRouter(router.getId());
        logConsole.append("Removed router " + router.getId());
        if (networkPanel.getCurrent() == router) networkPanel.show(null, topology);
        if (routingTablePanel.getCurrent() == router) routingTablePanel.show(null, topology, engine.getRoutingTable());
        canvas.clearSelection();
        engine.recomputeRoutes();
        canvas.redraw();
    }

    private void removeLinkAction(Link link) {
        topology.removeLink(link.getId());
        logConsole.append("Removed link " + link.getId());
        canvas.clearSelection();
        engine.recomputeRoutes();
        canvas.redraw();
    }

    private String nextRouterId() {
        int n = topology.routerCount() + 1;
        while (topology.getRouter("R" + n) != null) n++;
        return "R" + n;
    }

    private String nextLinkId() {
        int n = topology.linkCount() + 1;
        while (topology.getLink("L" + n) != null) n++;
        return "L" + n;
    }

    // ------------------------------------------------------------------ //
    // Clocks: simulation ticks + smooth packet animation
    // ------------------------------------------------------------------ //

    private void startClocks() {
        rebuildTimeline();

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNanos < 0) { lastFrameNanos = now; return; }
                double deltaMs = (now - lastFrameNanos) / 1_000_000.0;
                lastFrameNanos = now;
                double fraction = deltaMs / engine.getTickIntervalMs();
                canvas.advanceAnimations(fraction);
                canvas.redraw();
            }
        };
        animationTimer.start();
    }

    private void rebuildTimeline() {
        boolean wasRunning = simTimeline != null && controls.runPauseButton.isSelected();
        if (simTimeline != null) simTimeline.stop();

        simTimeline = new Timeline(new KeyFrame(Duration.millis(engine.getTickIntervalMs()), e -> doTickAndRefresh()));
        simTimeline.setCycleCount(Timeline.INDEFINITE);
        if (wasRunning) simTimeline.play();
    }

    private void doTickAndRefresh() {
        if (controls.aiTrainingCheck.isSelected()) {
            for (Router dest : topology.getRouters()) {
                if (dest.isUp()) aiOptimizer.trainEpisode(topology, dest.getId());
            }
        }

        engine.tick();
        updateStatLabels();

        if (networkPanel.getCurrent() != null) networkPanel.refresh(topology);
        if (routingTablePanel.getCurrent() != null) routingTablePanel.refresh(topology, engine.getRoutingTable());
        if (packetInspectorPanel.getCurrent() != null) packetInspectorPanel.refresh(topology, engine.getSimTimeMs());

        List<PerformanceSnapshot> history = engine.getStatistics().getHistory();
        if (!history.isEmpty()) {
            chartsPanel.addSnapshot(history.get(history.size() - 1));
        }
    }

    private void updateStatLabels() {
        var stats = engine.getStatistics();
        controls.simTimeLabel.setText(String.format("t = %,d ms", engine.getSimTimeMs()));
        controls.generatedLabel.setText("Generated: " + stats.getTotalGenerated());
        controls.deliveredLabel.setText("Delivered: " + stats.getTotalDelivered());
        controls.droppedLabel.setText("Dropped: " + stats.getTotalDropped());

        List<PerformanceSnapshot> history = stats.getHistory();
        if (!history.isEmpty()) {
            PerformanceSnapshot last = history.get(history.size() - 1);
            controls.pdrLabel.setText(String.format(Locale.US, "PDR: %.1f%%", last.packetDeliveryRatio * 100));
            controls.throughputLabel.setText(String.format(Locale.US, "Throughput: %.1f pkt/s", last.throughputPacketsPerSec));
        }
    }
}
