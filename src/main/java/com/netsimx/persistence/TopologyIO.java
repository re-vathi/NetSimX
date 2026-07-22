package com.netsimx.persistence;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Module 13 - Digital Twin Support: import/export {@link NetworkTopology}
 * as JSON so enterprise/campus network layouts can be captured, versioned,
 * and reloaded without rebuilding them by hand in the GUI each time.
 *
 * File shape:
 * <pre>
 * {
 *   "routers": [ {"id": "R1", "label": "Core-1", "x": 100, "y": 200, "queueCapacity": 64}, ... ],
 *   "links":   [ {"id": "L1", "a": "R1", "b": "R2", "cost": 1, "latencyMs": 5, "bandwidthPps": 100}, ... ]
 * }
 * </pre>
 */
public final class TopologyIO {

    private TopologyIO() {}

    public static void save(NetworkTopology topology, Path path) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();

        List<Object> routersJson = new ArrayList<>();
        for (Router r : topology.getRouters()) {
            Map<String, Object> rj = new LinkedHashMap<>();
            rj.put("id", r.getId());
            rj.put("label", r.getLabel());
            rj.put("x", r.getX());
            rj.put("y", r.getY());
            rj.put("queueCapacity", r.getQueueCapacity());
            rj.put("status", r.getStatus().name());
            routersJson.add(rj);
        }
        root.put("routers", routersJson);

        List<Object> linksJson = new ArrayList<>();
        for (Link l : topology.getLinks()) {
            Map<String, Object> lj = new LinkedHashMap<>();
            lj.put("id", l.getId());
            lj.put("a", l.getRouterAId());
            lj.put("b", l.getRouterBId());
            lj.put("cost", l.getCost());
            lj.put("latencyMs", l.getLatencyMs());
            lj.put("bandwidthPps", l.getBandwidthPps());
            linksJson.add(lj);
        }
        root.put("links", linksJson);

        Files.writeString(path, MiniJson.write(root), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static NetworkTopology load(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(text);

        NetworkTopology topology = new NetworkTopology();

        List<Object> routers = (List<Object>) root.getOrDefault("routers", List.of());
        for (Object o : routers) {
            Map<String, Object> rj = (Map<String, Object>) o;
            String id = (String) rj.get("id");
            String label = rj.containsKey("label") ? (String) rj.get("label") : id;
            double x = numberOr(rj.get("x"), 0);
            double y = numberOr(rj.get("y"), 0);
            int capacity = (int) numberOr(rj.get("queueCapacity"), 64);
            Router router = new Router(id, label, x, y, capacity);
            if (rj.containsKey("status") && "DOWN".equals(rj.get("status"))) {
                router.setStatus(Router.Status.DOWN);
            }
            topology.addRouter(router);
        }

        List<Object> links = (List<Object>) root.getOrDefault("links", List.of());
        for (Object o : links) {
            Map<String, Object> lj = (Map<String, Object>) o;
            String id = (String) lj.get("id");
            String a = (String) lj.get("a");
            String b = (String) lj.get("b");
            double cost = numberOr(lj.get("cost"), 1);
            double latency = numberOr(lj.get("latencyMs"), 5);
            double bandwidth = numberOr(lj.get("bandwidthPps"), 100);
            topology.addLink(new Link(id, a, b, cost, latency, bandwidth));
        }

        return topology;
    }

    private static double numberOr(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        return fallback;
    }
}
