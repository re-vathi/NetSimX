package com.netsimx.topology;

import com.netsimx.model.NetworkTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopologyGeneratorTest {

    @Test
    void emptyTemplateProducesNoRoutersOrLinks() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.EMPTY, 9);
        assertEquals(0, topo.routerCount());
        assertEquals(0, topo.linkCount());
    }

    @Test
    void starHasHubWithDegreeEqualToSpokeCount() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.STAR, 6);
        assertEquals(7, topo.routerCount()); // hub + 6 spokes
        assertEquals(6, topo.incidentLinks("R1").size());
    }

    @Test
    void meshConnectsEveryRouterToEveryOther() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.MESH, 5);
        assertEquals(5, topo.routerCount());
        assertEquals(5 * 4 / 2, topo.linkCount());
        for (var r : topo.getRouters()) {
            assertEquals(4, topo.incidentLinks(r.getId()).size(), "Every router in a 5-node mesh should connect to the other 4");
        }
    }

    @Test
    void meshSizeIsCappedRegardlessOfRequestedCount() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.MESH, 50);
        assertTrue(topo.routerCount() <= 8, "Mesh generator should cap size since edges grow O(n^2)");
    }

    @Test
    void everyRouterInARingHasExactlyTwoNeighbors() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.RING, 6);
        assertEquals(6, topo.routerCount());
        assertEquals(6, topo.linkCount());
        for (var r : topo.getRouters()) {
            assertEquals(2, topo.incidentLinks(r.getId()).size());
        }
    }

    @Test
    void treeHasOneFewerLinkThanRouters() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.TREE, 10);
        // A tree with N nodes always has exactly N-1 edges.
        assertEquals(topo.routerCount() - 1, topo.linkCount());
    }

    @Test
    void ispBackboneMatchesTheBundledSampleShape() {
        NetworkTopology topo = TopologyGenerator.generate(TopologyGenerator.Template.ISP_BACKBONE, 9);
        assertEquals(9, topo.routerCount());
        assertEquals(12, topo.linkCount());
        assertNotNull(topo.getRouter("R1"));
        assertEquals("Core-1", topo.getRouter("R1").getLabel());
    }

    @Test
    void everyGeneratedLinkReferencesRoutersThatActuallyExist() {
        for (TopologyGenerator.Template template : TopologyGenerator.Template.values()) {
            NetworkTopology topo = TopologyGenerator.generate(template, 7);
            for (var link : topo.getLinks()) {
                assertNotNull(topo.getRouter(link.getRouterAId()), template + ": dangling link endpoint A");
                assertNotNull(topo.getRouter(link.getRouterBId()), template + ": dangling link endpoint B");
            }
        }
    }
}
