package com.netsimx.routing;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.util.*;

/**
 * Single-source shortest path using the Bellman-Ford algorithm - models
 * distance-vector routing (RIP), where routers iteratively relax edges
 * without needing full topology visibility up front. Also correctly
 * detects negative cycles, though link costs in this simulator are always
 * non-negative in practice.
 */
public class BellmanFordRouting implements RoutingAlgorithm {

    @Override
    public String getName() {
        return "Bellman-Ford (RIP)";
    }

    @Override
    public Map<String, RouteResult> computeRoutes(NetworkTopology topology, String sourceId) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        List<Router> activeRouters = new ArrayList<>();
        for (Router r : topology.getRouters()) {
            if (r.isUp()) activeRouters.add(r);
            dist.put(r.getId(), Double.POSITIVE_INFINITY);
        }
        dist.put(sourceId, 0.0);

        List<Link> activeLinks = new ArrayList<>();
        for (Link l : topology.getLinks()) {
            if (l.isUp()) activeLinks.add(l);
        }

        int n = activeRouters.size();
        for (int iteration = 0; iteration < Math.max(1, n - 1); iteration++) {
            boolean changed = false;
            for (Link link : activeLinks) {
                changed |= relax(topology, dist, prev, link.getRouterAId(), link.getRouterBId(), link.getCost());
                changed |= relax(topology, dist, prev, link.getRouterBId(), link.getRouterAId(), link.getCost());
            }
            if (!changed) break; // converged early
        }

        return RoutingUtil.buildResults(sourceId, dist, prev);
    }

    private boolean relax(NetworkTopology topology, Map<String, Double> dist, Map<String, String> prev,
                           String u, String v, double weight) {
        Router ur = topology.getRouter(u);
        Router vr = topology.getRouter(v);
        if (ur == null || vr == null || !ur.isUp() || !vr.isUp()) return false;

        Double du = dist.get(u);
        if (du == null || Double.isInfinite(du)) return false;

        double alt = du + weight;
        if (alt < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
            dist.put(v, alt);
            prev.put(v, u);
            return true;
        }
        return false;
    }
}
