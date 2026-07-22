package com.netsimx.model;

import java.util.*;

/**
 * The network graph: routers as vertices, links as (weighted, bidirectional)
 * edges. This class is intentionally storage/algorithm-agnostic - routing
 * algorithms in {@code com.netsimx.routing} consume it read-only via the
 * adjacency helpers below.
 */
public class NetworkTopology {

    private final Map<String, Router> routers = new LinkedHashMap<>();
    private final Map<String, Link> links = new LinkedHashMap<>();
    /** routerId -> list of link IDs incident to it, for fast adjacency lookups. */
    private final Map<String, List<String>> adjacency = new LinkedHashMap<>();

    public Router addRouter(Router router) {
        routers.put(router.getId(), router);
        adjacency.putIfAbsent(router.getId(), new ArrayList<>());
        return router;
    }

    public void removeRouter(String routerId) {
        routers.remove(routerId);
        List<String> incident = new ArrayList<>(adjacency.getOrDefault(routerId, List.of()));
        for (String linkId : incident) {
            removeLink(linkId);
        }
        adjacency.remove(routerId);
    }

    public Link addLink(Link link) {
        links.put(link.getId(), link);
        adjacency.computeIfAbsent(link.getRouterAId(), k -> new ArrayList<>()).add(link.getId());
        adjacency.computeIfAbsent(link.getRouterBId(), k -> new ArrayList<>()).add(link.getId());
        return link;
    }

    public void removeLink(String linkId) {
        Link link = links.remove(linkId);
        if (link == null) return;
        List<String> a = adjacency.get(link.getRouterAId());
        if (a != null) a.remove(linkId);
        List<String> b = adjacency.get(link.getRouterBId());
        if (b != null) b.remove(linkId);
    }

    public Router getRouter(String id) { return routers.get(id); }
    public Link getLink(String id) { return links.get(id); }

    public Collection<Router> getRouters() { return routers.values(); }
    public Collection<Link> getLinks() { return links.values(); }

    public Optional<Link> findLinkBetween(String a, String b) {
        return links.values().stream().filter(l -> l.connects(a, b)).findFirst();
    }

    /** Neighbor router IDs reachable from {@code routerId} via UP links to UP routers only. */
    public List<String> activeNeighbors(String routerId) {
        List<String> result = new ArrayList<>();
        for (String linkId : adjacency.getOrDefault(routerId, List.of())) {
            Link link = links.get(linkId);
            if (link == null || !link.isUp()) continue;
            String other = link.otherEnd(routerId);
            Router r = routers.get(other);
            if (r != null && r.isUp()) result.add(other);
        }
        return result;
    }

    public List<Link> incidentLinks(String routerId) {
        List<Link> result = new ArrayList<>();
        for (String linkId : adjacency.getOrDefault(routerId, List.of())) {
            Link l = links.get(linkId);
            if (l != null) result.add(l);
        }
        return result;
    }

    public int routerCount() { return routers.size(); }
    public int linkCount() { return links.size(); }

    public void clear() {
        routers.clear();
        links.clear();
        adjacency.clear();
    }
}
