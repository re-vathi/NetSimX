package com.netsimx.simulation;

import com.netsimx.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private NetworkTopology topology;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        topology = new NetworkTopology();
        topology.addRouter(new Router("R1", 0, 0));
        topology.addRouter(new Router("R2", 1, 0));
        topology.addRouter(new Router("R3", 2, 0));

        topology.addLink(new Link("L1", "R1", "R2", 1, 5, 50));
        topology.addLink(new Link("L2", "R2", "R3", 1, 5, 50));

        engine = new SimulationEngine(topology);
        engine.recomputeRoutes();
    }

    @Test
    void udpPacketsAreDeliveredAcrossMultipleHops() {
        engine.getTrafficGenerator().addFlow(
                new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.VOICE, 1.0));

        for (int i = 0; i < 20; i++) engine.tick();

        assertTrue(engine.getStatistics().getTotalDelivered() > 0, "Expected at least one UDP packet delivered");
        assertEquals(0, engine.getStatistics().getTotalDropped(), "No drops expected on an uncongested 3-hop path");
    }

    @Test
    void tcpFlowGeneratesAcksBackToSource() {
        engine.getTrafficGenerator().addFlow(
                new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.WEB, 1.0));

        for (int i = 0; i < 20; i++) engine.tick();

        // TCP delivery should trigger an ACK, which itself counts as a second delivered packet.
        assertTrue(engine.getStatistics().getTotalDelivered() >= 2,
                "Expected both data packets and their ACKs to be delivered");
    }

    @Test
    void packetsAreDroppedWhenNoRouteExists() {
        topology.addRouter(new Router("R99", 9, 9)); // isolated
        engine.getTrafficGenerator().addFlow(
                new TrafficGenerator.Flow("R1", "R99", TrafficGenerator.TrafficType.EMAIL, 1.0));

        engine.tick();

        assertTrue(engine.getStatistics().getTotalDropped() > 0, "Expected drop due to no route to isolated router");
    }

    @Test
    void linkFailureTriggersRerouteAndRecovery() {
        engine.setLinkUp(topology.getLink("L1"), false);
        assertFalse(topology.getLink("L1").isUp());

        engine.setLinkUp(topology.getLink("L1"), true);
        assertTrue(topology.getLink("L1").isUp());
    }

    @Test
    void resetClearsCountersAndQueues() {
        engine.getTrafficGenerator().addFlow(
                new TrafficGenerator.Flow("R1", "R3", TrafficGenerator.TrafficType.FILE_TRANSFER, 1.0));
        for (int i = 0; i < 10; i++) engine.tick();
        assertTrue(engine.getStatistics().getTotalGenerated() > 0);

        engine.reset();

        assertEquals(0, engine.getStatistics().getTotalGenerated());
        assertEquals(0, engine.getStatistics().getTotalDelivered());
        assertEquals(0, engine.getStatistics().getTotalDropped());
        assertEquals(0, engine.getSimTimeMs());
    }
}
