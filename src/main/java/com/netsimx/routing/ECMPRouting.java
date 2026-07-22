package com.netsimx.routing;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Equal-Cost Multi-Path routing: first computes shortest-path distances
 * (Dijkstra), then reconstructs *all* paths achieving that minimum cost to
 * each destination. {@link #computeRoutes} (required by the interface)
 * picks one of them round-robin so the algorithm is still drop-in
 * compatible with single-path consumers; {@link #computeAllEqualCostPaths}
 * exposes the full set for the load-balancing module (Module 9) to spread
 * traffic across.
 */
public class ECMPRouting implements RoutingAlgorithm {

    /** Round-robin counters per (source,destination) pair for repeated calls. */
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private static final double EPSILON = 1e-6;

    @Override
    public String getName() {
        return "ECMP";
    }

    @Override
    public Map<String, RouteResult> computeRoutes(NetworkTopology topology, String sourceId) {
        Map<String, List<RouteResult>> allPaths = computeAllEqualCostPaths(topology, sourceId);
        Map<String, RouteResult> chosen = new LinkedHashMap<>();
        for (Map.Entry<String, List<RouteResult>> e : allPaths.entrySet()) {
            List<RouteResult> options = e.getValue();
            if (options.isEmpty()) continue;
            String key = sourceId + "->" + e.getKey();
            AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int idx = Math.floorMod(counter.getAndIncrement(), options.size());
            chosen.put(e.getKey(), options.get(idx));
        }
        return chosen;
    }

    /**
     * Computes, for every reachable destination, the full list of
     * minimum-cost paths from {@code sourceId} (usually 1, but >1 whenever
     * genuine equal-cost alternatives exist in the topology).
     */
    public Map<String, List<RouteResult>> computeAllEqualCostPaths(NetworkTopology topology, String sourceId) {
        // Step 1: standard Dijkstra distances.
        Map<String, Double> dist = new HashMap<>();
        for (Router r : topology.getRouters()) dist.put(r.getId(), Double.POSITIVE_INFINITY);
        dist.put(sourceId, 0.0);

        Set<String> visited = new HashSet<>();
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(sourceId);

        // predecessors: destination -> set of predecessor router IDs that lie on SOME shortest path
        Map<String, List<String>> predecessors = new HashMap<>();

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
                double dv = dist.getOrDefault(v, Double.POSITIVE_INFINITY);

                if (alt < dv - EPSILON) {
                    dist.put(v, alt);
                    predecessors.put(v, new ArrayList<>(List.of(u)));
                    pq.remove(v);
                    pq.add(v);
                } else if (Math.abs(alt - dv) < EPSILON) {
                    // tie: another equal-cost predecessor for v
                    predecessors.computeIfAbsent(v, k -> new ArrayList<>()).add(u);
                }
            }
        }

        // Step 2: reconstruct all equal-cost paths per destination via DFS over predecessors.
        Map<String, List<RouteResult>> result = new LinkedHashMap<>();
        for (Router r : topology.getRouters()) {
            String dest = r.getId();
            if (dest.equals(sourceId)) continue;
            double cost = dist.getOrDefault(dest, Double.POSITIVE_INFINITY);
            if (Double.isInfinite(cost)) continue;

            List<List<String>> paths = new ArrayList<>();
            reconstructPaths(dest, sourceId, predecessors, new LinkedList<>(), paths, 0);

            List<RouteResult> options = new ArrayList<>();
            for (List<String> p : paths) options.add(new RouteResult(p, cost));
            result.put(dest, options);
        }
        return result;
    }

    private void reconstructPaths(String current, String sourceId, Map<String, List<String>> predecessors,
                                   LinkedList<String> partial, List<List<String>> out, int depth) {
        partial.addFirst(current);
        if (current.equals(sourceId)) {
            out.add(new ArrayList<>(partial));
        } else if (depth < 64) { // guard against pathological graphs
            for (String pred : predecessors.getOrDefault(current, List.of())) {
                reconstructPaths(pred, sourceId, predecessors, partial, out, depth + 1);
                if (out.size() >= 8) break; // cap explosion of equal-cost paths for very meshy graphs
            }
        }
        partial.removeFirst();
    }
}
