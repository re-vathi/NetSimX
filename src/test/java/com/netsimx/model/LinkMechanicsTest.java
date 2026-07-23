package com.netsimx.model;

import com.netsimx.simulation.SimulationEngine;
import com.netsimx.simulation.TrafficGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinkMechanicsTest {

    @Test
    void hundredPercentLossProbabilityBlocksAllDeliveries() {
        NetworkTopology topo = new NetworkTopology();
        topo.addRouter(new Router("R1", 0, 0));
        topo.addRouter(new Router("R2", 1, 0));
        Link link = new Link("L1", "R1", "R2", 1, 5, 100);
        link.setLossProbability(1.0);
        topo.addLink(link);

        SimulationEngine engine = new SimulationEngine(topo);
        engine.recomputeRoutes();
        engine.getTrafficGenerator().addFlow(new TrafficGenerator.Flow("R1", "R2", TrafficGenerator.TrafficType.WEB, 1.0));

        for (int i = 0; i < 15; i++) engine.tick();

        assertEquals(0, engine.getStatistics().getTotalDelivered(),
                "No packets should be delivered across a 100% loss-probability link");
        assertTrue(engine.getStatistics().getTotalDropped() > 0, "Packets should have been dropped due to line error");
    }

    @Test
    void zeroLossProbabilityDeliversNormally() {
        NetworkTopology topo = new NetworkTopology();
        topo.addRouter(new Router("R1", 0, 0));
        topo.addRouter(new Router("R2", 1, 0));
        topo.addLink(new Link("L1", "R1", "R2", 1, 5, 100)); // lossProbability defaults to 0

        SimulationEngine engine = new SimulationEngine(topo);
        engine.recomputeRoutes();
        engine.getTrafficGenerator().addFlow(new TrafficGenerator.Flow("R1", "R2", TrafficGenerator.TrafficType.VOICE, 1.0));

        for (int i = 0; i < 15; i++) engine.tick();

        assertTrue(engine.getStatistics().getTotalDelivered() > 0, "Packets should be delivered with zero loss probability");
    }

    @Test
    void congestReducesEffectiveBandwidth() {
        Link link = new Link("L1", "R1", "R2", 1, 5, 100);
        link.congest(0.1);
        assertTrue(link.isCongested());
        assertEquals(10.0, link.getBandwidthPps(), 0.001);
    }

    @Test
    void releaseCongestionRestoresOriginalBandwidth() {
        Link link = new Link("L1", "R1", "R2", 1, 5, 100);
        link.congest(0.25);
        link.releaseCongestion();
        assertFalse(link.isCongested());
        assertEquals(100.0, link.getBandwidthPps(), 0.001);
    }

    @Test
    void lossProbabilityIsClampedToValidRange() {
        Link link = new Link("L1", "R1", "R2", 1, 5, 100);
        link.setLossProbability(1.5);
        assertEquals(1.0, link.getLossProbability(), 0.001);
        link.setLossProbability(-0.5);
        assertEquals(0.0, link.getLossProbability(), 0.001);
    }
}
