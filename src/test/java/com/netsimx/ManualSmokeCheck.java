package com.netsimx;

import com.netsimx.model.*;
import com.netsimx.routing.*;
import com.netsimx.simulation.*;

public class ManualSmokeCheck {
    public static void main(String[] args) throws Exception {
        NetworkTopology topo = new NetworkTopology();
        topo.addRouter(new Router("R1", 0, 0));
        topo.addRouter(new Router("R2", 100, 0));
        topo.addRouter(new Router("R3", 200, 0));
        topo.addRouter(new Router("R4", 100, 100));

        topo.addLink(new Link("L1", "R1", "R2", 1, 5, 50));
        topo.addLink(new Link("L2", "R2", "R3", 1, 5, 50));
        topo.addLink(new Link("L3", "R1", "R4", 1, 5, 50));
        topo.addLink(new Link("L4", "R4", "R3", 1, 5, 50));

        SimulationEngine engine = new SimulationEngine(topo);
        engine.addListener(new SimulationListener() {
            @Override public void onLog(String message) { System.out.println("[LOG] " + message); }
            @Override public void onPacketDropped(Packet packet, String reason) {
                System.out.println("[DROP] " + packet.getId() + " reason=" + reason);
            }
            @Override public void onPacketDelivered(Packet packet) {
                System.out.println("[DELIVERED] #" + packet.getId() + " delay=" + packet.endToEndDelayMs(packet.getDeliveredAtMs()) + "ms path=" + packet.getPath());
            }
        });

        engine.recomputeRoutes();
        engine.getTrafficGenerator().addFlow(new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.WEB, 0.8));
        engine.getTrafficGenerator().addFlow(new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.VOICE, 0.8));

        for (int i = 0; i < 50; i++) {
            engine.tick();
        }

        var stats = engine.getStatistics();
        System.out.println("Generated=" + stats.getTotalGenerated() + " Delivered=" + stats.getTotalDelivered() + " Dropped=" + stats.getTotalDropped());

        // Test Dijkstra vs Bellman-Ford agreement
        DijkstraRouting dijkstra = new DijkstraRouting();
        BellmanFordRouting bf = new BellmanFordRouting();
        var d = dijkstra.computeRoutes(topo, "R1").get("R3");
        var b = bf.computeRoutes(topo, "R1").get("R3");
        System.out.println("Dijkstra R1->R3: " + d);
        System.out.println("BellmanFord R1->R3: " + b);
        if (Math.abs(d.getTotalCost() - b.getTotalCost()) > 1e-6) {
            throw new AssertionError("Dijkstra/BellmanFord disagree!");
        }

        // Test failure + reroute
        Link l2 = topo.getLink("L2");
        engine.setLinkUp(l2, false);
        var afterFail = dijkstra.computeRoutes(topo, "R1").get("R3");
        System.out.println("After L2 down, R1->R3: " + afterFail);
        if (!afterFail.getPath().contains("R4")) {
            throw new AssertionError("Expected reroute via R4 after L2 failure!");
        }

        // Test ECMP multi-path
        ECMPRouting ecmp = new ECMPRouting();
        var allPaths = ecmp.computeAllEqualCostPaths(topo, "R1");
        System.out.println("ECMP paths R1->R4 area: " + allPaths.get("R3"));

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("netsimx-topo", ".json");
        com.netsimx.persistence.TopologyIO.save(topo, tmp);
        NetworkTopology reloaded = com.netsimx.persistence.TopologyIO.load(tmp);
        if (reloaded.routerCount() != topo.routerCount() || reloaded.linkCount() != topo.linkCount()) {
            throw new AssertionError("Topology JSON round-trip mismatch!");
        }
        System.out.println("JSON round-trip OK: " + reloaded.routerCount() + " routers, " + reloaded.linkCount() + " links");

        System.out.println("SMOKE TEST PASSED");
    }
}
