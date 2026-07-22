package com.netsimx.routing;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingAlgorithmsTest {

    private NetworkTopology topology;

    @BeforeEach
    void setUp() {
        topology = new NetworkTopology();
        topology.addRouter(new Router("R1", 0, 0));
        topology.addRouter(new Router("R2", 1, 0));
        topology.addRouter(new Router("R3", 2, 0));
        topology.addRouter(new Router("R4", 1, 1));

        topology.addLink(new Link("L1", "R1", "R2", 1, 5, 100));
        topology.addLink(new Link("L2", "R2", "R3", 1, 5, 100));
        topology.addLink(new Link("L3", "R1", "R4", 1, 5, 100));
        topology.addLink(new Link("L4", "R4", "R3", 1, 5, 100));
    }

    @Test
    void dijkstraFindsShortestPath() {
        var routes = new DijkstraRouting().computeRoutes(topology, "R1");
        assertEquals(2.0, routes.get("R3").getTotalCost(), 1e-9);
        assertEquals("R1", routes.get("R3").getPath().get(0));
        assertEquals("R3", routes.get("R3").getPath().get(routes.get("R3").getPath().size() - 1));
    }

    @Test
    void dijkstraAndBellmanFordAgreeOnCost() {
        var dijkstraRoutes = new DijkstraRouting().computeRoutes(topology, "R1");
        var bellmanFordRoutes = new BellmanFordRouting().computeRoutes(topology, "R1");

        for (String dest : dijkstraRoutes.keySet()) {
            assertEquals(dijkstraRoutes.get(dest).getTotalCost(),
                    bellmanFordRoutes.get(dest).getTotalCost(), 1e-9,
                    "Mismatch for destination " + dest);
        }
    }

    @Test
    void reroutesAfterLinkFailure() {
        topology.getLink("L2").setUp(false); // sever R2<->R3

        var routes = new DijkstraRouting().computeRoutes(topology, "R1");
        var toR3 = routes.get("R3");

        assertNotNull(toR3, "R3 should still be reachable via R4");
        assertTrue(toR3.getPath().contains("R4"), "Expected reroute via R4 after L2 failure");
    }

    @Test
    void unreachableDestinationIsOmitted() {
        topology.addRouter(new Router("R5", 5, 5)); // isolated, no links

        var routes = new DijkstraRouting().computeRoutes(topology, "R1");
        assertFalse(routes.containsKey("R5"), "Isolated router should not appear as a reachable destination");
    }

    @Test
    void downRouterIsExcludedFromRouting() {
        topology.getRouter("R2").setStatus(Router.Status.DOWN);

        var routes = new DijkstraRouting().computeRoutes(topology, "R1");
        var toR3 = routes.get("R3");

        assertNotNull(toR3);
        assertFalse(toR3.getPath().contains("R2"), "Path should avoid the down router");
        assertTrue(toR3.getPath().contains("R4"), "Should route via R4 instead");
    }

    @Test
    void ecmpFindsMultipleEqualCostPaths() {
        // Make both paths R1-R2-R3 and R1-R4-R3 cost exactly 2.
        Map<String, RoutingAlgorithm.RouteResult> ecmpRoutes = new ECMPRouting().computeRoutes(topology, "R1");
        assertNotNull(ecmpRoutes.get("R3"));

        ECMPRouting ecmp = new ECMPRouting();
        var allPaths = ecmp.computeAllEqualCostPaths(topology, "R1");
        assertEquals(2, allPaths.get("R3").size(), "Expected two equal-cost paths to R3");
    }
}
