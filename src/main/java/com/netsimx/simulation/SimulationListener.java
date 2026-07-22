package com.netsimx.simulation;

import com.netsimx.model.Link;
import com.netsimx.model.Packet;
import com.netsimx.model.Router;

/**
 * Callback interface the GUI (or any other observer, e.g. a headless
 * logger) implements to react to simulation events as they happen, rather
 * than polling. All callbacks fire on the simulation thread - implementors
 * that touch JavaFX nodes must hop back onto the FX thread themselves
 * (see {@code gui.NetSimXApp} for the Platform.runLater wrapping).
 */
public interface SimulationListener {

    default void onTick(long simTimeMs) {}

    default void onPacketCreated(Packet packet) {}

    /** Fired every time a packet moves from one router to the next hop. */
    default void onPacketHop(Packet packet, String fromRouterId, String toRouterId, Link viaLink) {}

    default void onPacketDelivered(Packet packet) {}

    default void onPacketDropped(Packet packet, String reason) {}

    default void onLinkStatusChanged(Link link, boolean up) {}

    default void onRouterStatusChanged(Router router, boolean up) {}

    default void onRoutesRecomputed(long simTimeMs) {}

    /** Free-text log line for the GUI's console/log panel. */
    default void onLog(String message) {}
}
