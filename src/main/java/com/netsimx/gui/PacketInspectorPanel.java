package com.netsimx.gui;

import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Packet;
import com.netsimx.model.Router;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * "Packet Inspector" tab: details for the last packet clicked on the
 * canvas. Holds a direct reference to the {@link Packet} object, which the
 * simulation engine keeps mutating (TTL, hop index, state) until it's
 * delivered/dropped - so this panel reflects live state for as long as the
 * packet is in flight, and freezes on its final state once it isn't.
 */
public class PacketInspectorPanel extends VBox {

    private final Label titleLabel = new Label("No packet selected");
    private final GridPane grid = new GridPane();
    private final Label[] valueLabels = new Label[10];
    private static final String[] FIELD_NAMES = {
            "Source", "Destination", "Protocol", "TTL", "Size (bytes)",
            "Priority", "Current Router", "Delay (ms)", "State", "Checksum"
    };

    private Packet current;

    public PacketInspectorPanel() {
        setSpacing(10);
        setPadding(new Insets(10));

        titleLabel.setStyle("-fx-text-fill: #e8f1fa; -fx-font-size: 16px; -fx-font-weight: bold;");

        grid.setHgap(12);
        grid.setVgap(8);
        for (int i = 0; i < FIELD_NAMES.length; i++) {
            Label name = new Label(FIELD_NAMES[i]);
            name.setStyle("-fx-text-fill: #8fb4d6; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label value = new Label("-");
            value.setStyle("-fx-text-fill: #d6e4f0; -fx-font-size: 12px;");
            valueLabels[i] = value;
            grid.add(name, 0, i);
            grid.add(value, 1, i);
        }

        Label hint = new Label("Click any moving packet dot on the canvas to inspect it live.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6d84a0; -fx-font-size: 11px; -fx-font-style: italic;");

        getChildren().addAll(titleLabel, new Separator(), grid, hint);
    }

    /** Clears the current selection (topology/nowMs are irrelevant when current is null). */
    public void show(Packet packet) {
        this.current = packet;
        refresh(null, 0);
    }

    public void show(Packet packet, NetworkTopology topology, long nowMs) {
        this.current = packet;
        refresh(topology, nowMs);
    }

    public Packet getCurrent() { return current; }

    /**
     * Refresh the displayed fields from the live packet object.
     * @param topology used to resolve the current router's display label; may be null
     * @param nowMs current simulated time, for computing live delay on in-flight packets
     */
    public void refresh(NetworkTopology topology, long nowMs) {
        if (current == null) {
            titleLabel.setText("No packet selected");
            for (Label l : valueLabels) l.setText("-");
            return;
        }

        titleLabel.setText("Packet #" + current.getId() + (current.isAck() ? "  (ACK)" : ""));

        String currentRouterId = current.currentRouterId();
        String currentRouterLabel = currentRouterId;
        if (topology != null && currentRouterId != null) {
            Router r = topology.getRouter(currentRouterId);
            if (r != null) currentRouterLabel = r.getLabel() + " (" + r.getId() + ")";
        }

        long delay = current.endToEndDelayMs(nowMs);

        String[] values = {
                current.getSourceId(),
                current.getDestinationId(),
                current.getProtocol().toString() + (current.isRetransmission() ? " (retransmit)" : ""),
                String.valueOf(current.getTtl()),
                String.valueOf(current.getSizeBytes()),
                current.getPriority().getDisplayName(),
                currentRouterLabel != null ? currentRouterLabel : "-",
                String.valueOf(delay),
                current.getState().toString(),
                current.computeChecksum() + (current.isChecksumValid() ? "  OK" : "  FAIL"),
        };
        for (int i = 0; i < values.length; i++) valueLabels[i].setText(values[i]);

        boolean dropped = current.getState() == Packet.State.DROPPED || current.getState() == Packet.State.EXPIRED;
        valueLabels[8].setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                (dropped ? "#ff6f6f" : current.getState() == Packet.State.DELIVERED ? "#43cf94" : "#d6e4f0"));
    }
}
