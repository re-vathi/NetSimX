package com.netsimx.routing;

import com.netsimx.model.NetworkTopology;
import java.util.List;
import java.util.Map;

/**
 * A path-finding strategy over a {@link NetworkTopology}. Implementations
 * only need to consider UP routers and UP links reachable from the source
 * - failed links/routers are simply absent from the graph they see.
 */
public interface RoutingAlgorithm {

    /** Human-readable protocol name, e.g. "Dijkstra (OSPF)". */
    String getName();

    /**
     * Compute the best single path from {@code sourceId} to every other
     * reachable router, given the current topology state.
     * @return map of destination router ID -> RouteResult (unreachable
     *         destinations are simply absent from the map).
     */
    Map<String, RouteResult> computeRoutes(NetworkTopology topology, String sourceId);

    /**
     * Result descriptor for a single computed route.
     */
    final class RouteResult {
        private final List<String> path;   // router IDs, source..destination inclusive
        private final double totalCost;

        public RouteResult(List<String> path, double totalCost) {
            this.path = path;
            this.totalCost = totalCost;
        }

        public List<String> getPath() { return path; }
        public double getTotalCost() { return totalCost; }

        @Override
        public String toString() {
            return "RouteResult{path=" + path + ", cost=" + totalCost + "}";
        }
    }
}
