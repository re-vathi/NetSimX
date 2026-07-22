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
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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

    private Timeline simTimeline;
    private AnimationTimer animationTimer;
    private long lastFrameNanos = -1;

    private final QLearningRouteOptimizer aiOptimizer = new QLearningRouteOptimizer();
    private Router pendingLinkSource = null;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        topology = loadInitialTopology();
        engine = new SimulationEngine(topology);

        canvas = new TopologyCanvas(900, 650);
        canvas.setTopology(topology);
        chartsPanel = new ChartsPanel();
        logConsole = new LogConsole();
        controls = new ControlPanel();

        wireEngineListeners();
        wireControlHandlers();
        wireCanvasHandlers();

        var visualBounds = Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.min(1400, visualBounds.getWidth() * 0.95);
        double windowHeight = Math.min(820, visualBounds.getHeight() * 0.9);

        BorderPane root = buildLayout();

        Scene scene = new Scene(root, windowWidth, windowHeight);
        var cssUrl = getClass().getResource("/com/netsimx/gui/dashboard.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("NetSimX \u2014 Intelligent Network Routing & Traffic Simulator");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        engine.recomputeRoutes();
        buildDefaultDemoFlows();

        startClocks();
        logConsole.append("NetSimX ready. " + topology.routerCount() + " routers, " + topology.linkCount() + " links loaded.");

        stage.setOnCloseRequest(e -> {
            if (simTimeline != null) simTimeline.stop();
            if (animationTimer != null) animationTimer.stop();
        });
    }

    // ------------------------------------------------------------------ //
    // Layout
    // ------------------------------------------------------------------ //

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();

        Pane canvasHost = new Pane(canvas);
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        ScrollPane canvasScroll = new ScrollPane(canvasHost);
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);

        ScrollPane chartsScroll = new ScrollPane(chartsPanel);
        chartsScroll.setFitToWidth(true);

        Tab chartsTab = new Tab("Live Charts", chartsScroll);
        chartsTab.setClosable(false);
        Tab logTab = new Tab("Event Log", logConsole);
        logTab.setClosable(false);
        TabPane rightTabs = new TabPane(chartsTab, logTab);
        rightTabs.setPrefWidth(380);

        SplitPane split = new SplitPane(controls, canvasScroll, rightTabs);
        split.setDividerPositions(0.19, 0.75);

        root.setCenter(split);
        return root;
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

    private void buildDefaultDemoFlows() {
        if (topology.getRouter("R1") != null && topology.getRouter("R3") != null) {
            engine.getTrafficGenerator().addFlow(
                    new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.WEB, 0.2));
            controls.flowsList.getItems().add("R1 -> R3 [WEB]");
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
                topology.removeRouter(r.getId());
                logConsole.append("Removed router " + r.getId());
            } else if (l != null) {
                topology.removeLink(l.getId());
                logConsole.append("Removed link " + l.getId());
            } else {
                return;
            }
            canvas.clearSelection();
            engine.recomputeRoutes();
            canvas.redraw();
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
            if (!controls.addLinkModeButton.isSelected()) return;
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
        });

        canvas.setOnRouterToggleRequested((router, up) -> {
            engine.setRouterUp(router, up);
            canvas.redraw();
        });
        canvas.setOnLinkToggleRequested((link, up) -> {
            engine.setLinkUp(link, up);
            canvas.redraw();
        });
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
