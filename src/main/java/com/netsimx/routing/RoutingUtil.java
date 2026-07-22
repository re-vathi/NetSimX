package com.netsimx.routing;

import java.util.*;

/** Shared helpers used by the concrete {@link RoutingAlgorithm} implementations. */
final class RoutingUtil {

    private RoutingUtil() {}

    static Map<String, RoutingAlgorithm.RouteResult> buildResults(
            String sourceId, Map<String, Double> dist, Map<String, String> prev) {

        Map<String, RoutingAlgorithm.RouteResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : dist.entrySet()) {
            String dest = e.getKey();
            double cost = e.getValue();
            if (dest.equals(sourceId)) continue;
            if (Double.isInfinite(cost)) continue; // unreachable

            LinkedList<String> path = new LinkedList<>();
            String cur = dest;
            boolean ok = true;
            while (cur != null && !cur.equals(sourceId)) {
                path.addFirst(cur);
                cur = prev.get(cur);
                if (path.size() > dist.size() + 1) { ok = false; break; } // cycle guard
            }
            if (!ok || cur == null) continue;
            path.addFirst(sourceId);

            results.put(dest, new RoutingAlgorithm.RouteResult(path, cost));
        }
        return results;
    }
}
