package com.netsimx.gui;

import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;
import com.netsimx.routing.RoutingAlgorithm;
import com.netsimx.routing.RoutingTable;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Routing Table" inspector tab: the live, per-destination routing table
 * as currently known by a selected router - destination, next hop, and
 * path metric (cost), straight from whichever {@link RoutingAlgorithm} is
 * active. Refreshes automatically on every tick and whenever routes are
 * recomputed (topology change or algorithm switch).
 */
public class RoutingTablePanel extends VBox {

    public static class RouteRow {
        private final SimpleStringProperty destination;
        private final SimpleStringProperty nextHop;
        private final SimpleStringProperty metric;
        private final SimpleStringProperty hops;

        RouteRow(String destination, String nextHop, String metric, String hops) {
            this.destination = new SimpleStringProperty(destination);
            this.nextHop = new SimpleStringProperty(nextHop);
            this.metric = new SimpleStringProperty(metric);
            this.hops = new SimpleStringProperty(hops);
        }

        public String getDestination() { return destination.get(); }
        public String getNextHop() { return nextHop.get(); }
        public String getMetric() { return metric.get(); }
        public String getHops() { return hops.get(); }
    }

    private final Label titleLabel = new Label("No router selected");
    private final TableView<RouteRow> table = new TableView<>();
    private Router current;

    @SuppressWarnings("unchecked")
    public RoutingTablePanel() {
        setSpacing(8);
        setPadding(new Insets(10));

        titleLabel.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 16px; -fx-font-weight: bold;");

        TableColumn<RouteRow, String> destCol = new TableColumn<>("Destination");
        destCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDestination()));
        TableColumn<RouteRow, String> nextHopCol = new TableColumn<>("Next Hop");
        nextHopCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNextHop()));
        TableColumn<RouteRow, String> metricCol = new TableColumn<>("Metric");
        metricCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMetric()));
        TableColumn<RouteRow, String> hopsCol = new TableColumn<>("Hops");
        hopsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getHops()));

        table.getColumns().addAll(destCol, nextHopCol, metricCol, hopsCol);
        table.setPrefHeight(400);
        table.setPlaceholder(new Label("No routes"));

        getChildren().addAll(titleLabel, new Separator(), table);
    }

    public void show(Router router, NetworkTopology topology, RoutingTable routingTable) {
        this.current = router;
        refresh(topology, routingTable);
    }

    public Router getCurrent() { return current; }

    public void refresh(NetworkTopology topology, RoutingTable routingTable) {
        if (current == null) {
            titleLabel.setText("No router selected");
            table.getItems().clear();
            table.setPlaceholder(new Label("Left-click a router to see its routing table."));
            return;
        }

        titleLabel.setText(current.getLabel() + "  (" + current.getId() + ")  \u2014  " + routingTable.getAlgorithm().getName());

        Map<String, RoutingAlgorithm.RouteResult> routes = routingTable.getRoutesFrom(current.getId());
        List<RouteRow> rows = new ArrayList<>();
        for (var entry : routes.entrySet()) {
            String destId = entry.getKey();
            RoutingAlgorithm.RouteResult route = entry.getValue();
            List<String> path = route.getPath();
            String nextHopId = path.size() > 1 ? path.get(1) : destId;

            Router destRouter = topology.getRouter(destId);
            Router nextHopRouter = topology.getRouter(nextHopId);

            rows.add(new RouteRow(
                    destRouter != null ? destRouter.getLabel() : destId,
                    nextHopRouter != null ? nextHopRouter.getLabel() : nextHopId,
                    String.format("%.1f", route.getTotalCost()),
                    String.valueOf(Math.max(0, path.size() - 1))
            ));
        }
        rows.sort((a, b) -> a.getDestination().compareToIgnoreCase(b.getDestination()));
        table.getItems().setAll(rows);
    }
}
