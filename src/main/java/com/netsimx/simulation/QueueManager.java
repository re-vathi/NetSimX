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

    /** Call once per simulation tick after packets have been enqueued/dequeued. */
    public void sample(NetworkTopology topology) {
        for (Router r : topology.getRouters()) {
            int size = r.getQueueSize();
            highWaterMarks.merge(r.getId(), size, Math::max);
        }
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
    }
}
