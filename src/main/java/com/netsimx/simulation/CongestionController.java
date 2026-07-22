package com.netsimx.simulation;

import com.netsimx.model.Link;

/**
 * Converts a link's configured bandwidth (packets/sec) plus the
 * simulation's tick rate into a per-tick transmit budget, and applies
 * utilization decay for links that go idle - this is what actually makes
 * {@link Link#getUtilization()} rise and fall visibly in the dashboard as
 * traffic load changes.
 */
public class CongestionController {

    private final double ticksPerSecond;

    public CongestionController(double ticksPerSecond) {
        this.ticksPerSecond = ticksPerSecond;
    }

    /** How many packets a given link may carry during a single tick, given its bandwidth cap. */
    public int transmitBudgetForTick(Link link) {
        if (!link.isUp()) return 0;
        double perTick = link.getBandwidthPps() / ticksPerSecond;
        return Math.max(1, (int) Math.round(perTick));
    }

    public void afterTick(Link link, int packetsSentThisTick) {
        if (packetsSentThisTick > 0) {
            for (int i = 0; i < packetsSentThisTick; i++) {
                link.recordTraffic(ticksPerSecond);
            }
        } else {
            link.decayUtilization();
        }
    }
}
