package com.netsimx.simulation;

import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks queue-related congestion metrics across all routers: current
 * occupancy, high-water marks, and drop counts. The actual bounded queue
 * lives on {@link Router} itself (kept close to the data it guards); this
 * class aggregates that state for the analytics/GUI layers so they don't
 * need to walk every router by hand.
 */
public class QueueManager {

    private final Map<String, Integer> highWaterMarks = new LinkedHashMap<>();

    /** Threshold above which a router counts as "congested" for event tracking. */
    private static final double CONGESTION_THRESHOLD = 0.75;
    private boolean wasCongested = false;
    private long congestionEventCount = 0;

    /** Call once per simulation tick after packets have been enqueued/dequeued. */
    public void sample(NetworkTopology topology) {
        for (Router r : topology.getRouters()) {
            int size = r.getQueueSize();
            highWaterMarks.merge(r.getId(), size, Math::max);
        }

        // Episode-based congestion tracking: count each *onset* (not-congested -> congested
        // transition) as one event, rather than counting every tick spent congested - a
        // 50-tick traffic jam should read as "1 event", not "50 events".
        boolean isCongested = congestedRouterFraction(topology, CONGESTION_THRESHOLD) > 0;
        if (isCongested && !wasCongested) {
            congestionEventCount++;
        }
        wasCongested = isCongested;
    }

    /** Total number of distinct congestion episodes observed so far - for report generation. */
    public long getCongestionEventCount() {
        return congestionEventCount;
    }

    public int getHighWaterMark(String routerId) {
        return highWaterMarks.getOrDefault(routerId, 0);
    }

    /** Fraction of routers currently above {@code thresholdOccupancy} (0..1) - a simple congestion indicator. */
    public double congestedRouterFraction(NetworkTopology topology, double thresholdOccupancy) {
        int total = 0, congested = 0;
        for (Router r : topology.getRouters()) {
            total++;
            if (r.getQueueOccupancy() >= thresholdOccupancy) congested++;
        }
        return total == 0 ? 0.0 : (double) congested / total;
    }

    public double averageOccupancy(NetworkTopology topology) {
        int total = 0;
        double sum = 0;
        for (Router r : topology.getRouters()) {
            sum += r.getQueueOccupancy();
            total++;
        }
        return total == 0 ? 0.0 : sum / total;
    }

    public void reset() {
        highWaterMarks.clear();
        wasCongested = false;
        congestionEventCount = 0;
    }
}
