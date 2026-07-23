package com.netsimx.routing;

import com.netsimx.model.NetworkTopology;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Caches computed routes (source -> destination -> RouteResult) for the
 * currently selected {@link RoutingAlgorithm} and exposes a
 * {@link #recompute} entry point the simulation engine calls whenever the
 * topology changes (link/router up, down, added, removed) - modeling how
 * real routing protocols reconverge after a change.
 */
public class RoutingTable {

    private RoutingAlgorithm algorithm;
    private final Map<String, Map<String, RoutingAlgorithm.RouteResult>> table = new HashMap<>();
    private long lastRecomputeTimeMs = 0;
    private int recomputeCount = 0;

    public RoutingTable(RoutingAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public RoutingAlgorithm getAlgorithm() { return algorithm; }

    public void setAlgorithm(RoutingAlgorithm algorithm) {
        this.algorithm = algorithm;
        table.clear();
    }

    /** Recompute routes from every router as a source. Call after any topology change. */
    public void recompute(NetworkTopology topology, long nowMs) {
        table.clear();
        for (var router : topology.getRouters()) {
            if (!router.isUp()) continue;
            table.put(router.getId(), algorithm.computeRoutes(topology, router.getId()));
        }
        lastRecomputeTimeMs = nowMs;
        recomputeCount++;
    }

    public Optional<RoutingAlgorithm.RouteResult> lookup(String sourceId, String destinationId) {
        Map<String, RoutingAlgorithm.RouteResult> fromSource = table.get(sourceId);
        if (fromSource == null) return Optional.empty();
        return Optional.ofNullable(fromSource.get(destinationId));
    }

    /**
     * All computed routes originating at {@code sourceId}, keyed by
     * destination router ID. Used by the Routing Table inspector tab.
     * Returns an empty map if the router is unknown or currently down.
     */
    public Map<String, RoutingAlgorithm.RouteResult> getRoutesFrom(String sourceId) {
        return table.getOrDefault(sourceId, Map.of());
    }

    public long getLastRecomputeTimeMs() { return lastRecomputeTimeMs; }
    public int getRecomputeCount() { return recomputeCount; }
}
