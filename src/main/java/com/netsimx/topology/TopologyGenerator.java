package com.netsimx.topology;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

/**
 * Procedural topology generators backing the New Simulation Wizard's
 * "Topology" template picker. Each generator produces a ready-to-simulate
 * {@link NetworkTopology} with sane default link costs/latency/bandwidth,
 * laid out with reasonable (x,y) positions for immediate canvas display
 * without the user needing to drag anything into place first.
 */
public final class TopologyGenerator {

    private TopologyGenerator() {}

    public enum Template {
        EMPTY("Empty", "Start from a blank canvas and build your own."),
        STAR("Star", "One central hub router connected to N spokes."),
        MESH("Mesh", "Every router directly connected to every other router."),
        RING("Ring", "Routers connected in a single closed loop."),
        TREE("Tree", "A 2-level hierarchical tree (root -> branches -> leaves)."),
        ISP_BACKBONE("ISP Backbone", "A core/distribution/access 3-tier topology (same shape as the bundled sample network).");

        public final String label;
        public final String description;

        Template(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static NetworkTopology generate(Template template, int routerCount) {
        return switch (template) {
            case EMPTY -> empty();
            case STAR -> star(Math.max(2, routerCount));
            case MESH -> mesh(Math.max(2, Math.min(routerCount, 8))); // cap mesh size - edges grow O(n^2)
            case RING -> ring(Math.max(3, routerCount));
            case TREE -> tree(Math.max(3, routerCount));
            case ISP_BACKBONE -> ispBackbone();
        };
    }

    private static NetworkTopology empty() {
        return new NetworkTopology();
    }

    private static NetworkTopology star(int spokeCount) {
        NetworkTopology topo = new NetworkTopology();
        double centerX = 450, centerY = 300, radius = 220;

        topo.addRouter(new Router("R1", "Hub", centerX, centerY, 64));
        for (int i = 0; i < spokeCount; i++) {
            double angle = 2 * Math.PI * i / spokeCount;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            String id = "R" + (i + 2);
            topo.addRouter(new Router(id, "Spoke-" + (i + 1), x, y, 48));
            topo.addLink(new Link("L" + (i + 1), "R1", id, 1, 5, 100));
        }
        return topo;
    }

    private static NetworkTopology mesh(int count) {
        NetworkTopology topo = new NetworkTopology();
        double centerX = 450, centerY = 300, radius = 220;

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            topo.addRouter(new Router("R" + (i + 1), "R" + (i + 1), x, y, 48));
        }
        int linkNum = 1;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                topo.addLink(new Link("L" + (linkNum++), "R" + (i + 1), "R" + (j + 1), 1, 5, 80));
            }
        }
        return topo;
    }

    private static NetworkTopology ring(int count) {
        NetworkTopology topo = new NetworkTopology();
        double centerX = 450, centerY = 300, radius = 220;

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            topo.addRouter(new Router("R" + (i + 1), "R" + (i + 1), x, y, 48));
        }
        for (int i = 0; i < count; i++) {
            String a = "R" + (i + 1);
            String b = "R" + (((i + 1) % count) + 1);
            topo.addLink(new Link("L" + (i + 1), a, b, 1, 5, 100));
        }
        return topo;
    }

    private static NetworkTopology tree(int totalRouters) {
        NetworkTopology topo = new NetworkTopology();
        topo.addRouter(new Router("R1", "Root", 450, 100, 64));

        int remaining = totalRouters - 1;
        int branchCount = Math.max(2, Math.min(4, remaining));
        int leavesPerBranch = Math.max(1, (remaining - branchCount) / Math.max(1, branchCount));

        int nextId = 2;
        int linkNum = 1;
        double branchSpacing = 800.0 / (branchCount + 1);

        for (int b = 0; b < branchCount && (nextId - 1) < totalRouters; b++) {
            double branchX = 50 + branchSpacing * (b + 1);
            String branchId = "R" + nextId++;
            topo.addRouter(new Router(branchId, "Branch-" + (b + 1), branchX, 300, 56));
            topo.addLink(new Link("L" + linkNum++, "R1", branchId, 1, 4, 120));

            double leafSpacing = branchSpacing / (leavesPerBranch + 1);
            for (int l = 0; l < leavesPerBranch && (nextId - 1) < totalRouters; l++) {
                double leafX = branchX - branchSpacing / 2 + leafSpacing * (l + 1);
                String leafId = "R" + nextId++;
                topo.addRouter(new Router(leafId, "Leaf-" + (b + 1) + "." + (l + 1), leafX, 500, 32));
                topo.addLink(new Link("L" + linkNum++, branchId, leafId, 2, 6, 80));
            }
        }
        return topo;
    }

    /** Same 3-tier shape as the bundled config/sample-network.json, generated programmatically. */
    private static NetworkTopology ispBackbone() {
        NetworkTopology topo = new NetworkTopology();
        String[][] routers = {
                {"R1", "Core-1", "150", "120"}, {"R2", "Core-2", "400", "120"},
                {"R3", "Edge-A", "650", "60"}, {"R4", "Edge-B", "650", "180"},
                {"R5", "Dist-1", "275", "280"}, {"R6", "Dist-2", "525", "280"},
                {"R7", "Access-1", "150", "420"}, {"R8", "Access-2", "400", "420"}, {"R9", "Access-3", "650", "420"},
        };
        int[] capacities = {64, 64, 48, 48, 56, 56, 32, 32, 32};
        for (int i = 0; i < routers.length; i++) {
            topo.addRouter(new Router(routers[i][0], routers[i][1],
                    Double.parseDouble(routers[i][2]), Double.parseDouble(routers[i][3]), capacities[i]));
        }
        Object[][] links = {
                {"L1", "R1", "R2", 1.0, 2.0, 200.0}, {"L2", "R2", "R3", 1.0, 3.0, 150.0},
                {"L3", "R2", "R4", 1.0, 3.0, 150.0}, {"L4", "R1", "R5", 2.0, 4.0, 120.0},
                {"L5", "R2", "R6", 2.0, 4.0, 120.0}, {"L6", "R5", "R6", 1.0, 2.0, 150.0},
                {"L7", "R5", "R7", 1.0, 3.0, 100.0}, {"L8", "R5", "R8", 2.0, 4.0, 90.0},
                {"L9", "R6", "R8", 2.0, 4.0, 90.0}, {"L10", "R6", "R9", 1.0, 3.0, 100.0},
                {"L11", "R3", "R9", 3.0, 6.0, 80.0}, {"L12", "R4", "R9", 2.0, 4.0, 90.0},
        };
        for (Object[] l : links) {
            topo.addLink(new Link((String) l[0], (String) l[1], (String) l[2],
                    (double) l[3], (double) l[4], (double) l[5]));
        }
        return topo;
    }
}
