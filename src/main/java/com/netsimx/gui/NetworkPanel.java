package com.netsimx.gui;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * "Network" inspector tab: everything about a single selected router -
 * status, queue occupancy, cumulative packet counts, and its interfaces
 * (incident links) with each neighbor's live bandwidth/up-down state.
 * Call {@link #show(Router, NetworkTopology)} on selection, and
 * {@link #refresh(NetworkTopology)} every tick to keep it live.
 */
public class NetworkPanel extends VBox {

    private final Label titleLabel = new Label("No router selected");
    private final Label statusLabel = new Label();
    private final Label queueLabel = new Label();
    private final Label countsLabel = new Label();
    private final Label neighborsLabel = new Label();
    private final ListView<String> interfacesList = new ListView<>();

    private Router current;

    public NetworkPanel() {
        setSpacing(8);
        setPadding(new Insets(10));

        titleLabel.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 16px; -fx-font-weight: bold;");
        for (Label l : new Label[]{statusLabel, queueLabel, countsLabel, neighborsLabel}) {
            l.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 12px;");
            l.setWrapText(true);
        }
        Label ifaceHeader = new Label("Interfaces");
        ifaceHeader.setStyle("-fx-text-fill: #8fb4d6; -fx-font-weight: bold; -fx-font-size: 11px;");
        interfacesList.setPrefHeight(220);
        interfacesList.setStyle("-fx-control-inner-background: #10141c; -fx-text-fill: #d6e4f0;");

        getChildren().addAll(titleLabel, new Separator(), statusLabel, queueLabel, countsLabel,
                new Separator(), neighborsLabel, ifaceHeader, interfacesList);
    }

    public void show(Router router, NetworkTopology topology) {
        this.current = router;
        refresh(topology);
    }

    public Router getCurrent() { return current; }

    public void refresh(NetworkTopology topology) {
        if (current == null) {
            titleLabel.setText("No router selected");
            statusLabel.setText("");
            queueLabel.setText("");
            countsLabel.setText("");
            neighborsLabel.setText("Left-click a router on the canvas to inspect it.");
            interfacesList.getItems().clear();
            return;
        }

        titleLabel.setText(current.getLabel() + "  (" + current.getId() + ")");
        statusLabel.setText("Status: " + (current.isUp() ? "UP" : "DOWN"));
        statusLabel.setStyle("-fx-text-fill: " + (current.isUp() ? "#43cf94" : "#ff6f6f") + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        queueLabel.setText(String.format("Queue: %d / %d  (%.0f%% full)",
                current.getQueueSize(), current.getQueueCapacity(), current.getQueueOccupancy() * 100));
        countsLabel.setText(String.format("Forwarded: %d    Dropped: %d",
                current.getPacketsForwarded(), current.getPacketsDropped()));

        List<String> neighbors = topology.activeNeighbors(current.getId());
        List<String> neighborLabels = new ArrayList<>();
        for (String nid : neighbors) {
            Router n = topology.getRouter(nid);
            neighborLabels.add(n != null ? n.getLabel() : nid);
        }
        neighborsLabel.setText("Active neighbors (" + neighborLabels.size() + "): " +
                (neighborLabels.isEmpty() ? "none" : String.join(", ", neighborLabels)));

        interfacesList.getItems().clear();
        for (Link link : topology.incidentLinks(current.getId())) {
            String otherId = link.otherEnd(current.getId());
            Router other = topology.getRouter(otherId);
            String otherLabel = other != null ? other.getLabel() : otherId;
            String state = link.isUp() ? "UP" : "DOWN";
            String congested = link.isCongested() ? "  [congested]" : "";
            interfacesList.getItems().add(String.format("%s -> %s  |  %s  |  %.0f pkt/s  |  util %.0f%%%s",
                    link.getId(), otherLabel, state, link.getBandwidthPps(), link.getUtilization() * 100, congested));
        }
    }
}
