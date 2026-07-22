package com.netsimx.routing;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.util.*;

/**
 * Single-source shortest path using Dijkstra's algorithm with a binary
 * heap priority queue - models link-state routing (OSPF), where every
 * router is assumed to have full topology visibility and picks the
 * globally lowest-cost path.
 */
public class DijkstraRouting implements RoutingAlgorithm {

    @Override
    public String getName() {
        return "Dijkstra (OSPF)";
    }

    @Override
    public Map<String, RouteResult> computeRoutes(NetworkTopology topology, String sourceId) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (Router r : topology.getRouters()) {
            dist.put(r.getId(), Double.POSITIVE_INFINITY);
        }
        dist.put(sourceId, 0.0);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(sourceId);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (visited.contains(u)) continue;
            visited.add(u);

            Router ur = topology.getRouter(u);
            if (ur == null || !ur.isUp()) continue;

            for (Link link : topology.incidentLinks(u)) {
                if (!link.isUp()) continue;
                String v = link.otherEnd(u);
                Router vr = topology.getRouter(v);
                if (vr == null || !vr.isUp()) continue;

                double alt = dist.get(u) + link.getCost();
                if (alt < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    pq.remove(v);
                    pq.add(v);
                }
            }
        }

        return RoutingUtil.buildResults(sourceId, dist, prev);
    }
}
