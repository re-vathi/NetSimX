package com.netsimx.analytics;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Packet;
import com.netsimx.model.Router;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Module 11 - Performance Analytics. Accumulates counters as the
 * simulation runs and produces {@link PerformanceSnapshot}s on demand
 * (typically once per GUI refresh tick). Keeps a bounded rolling history
 * for the dashboard's live charts.
 */
public class StatisticsCollector {

    private long totalGenerated = 0;
    private long totalDelivered = 0;
    private long totalDropped = 0;

    private double sumEndToEndDelayMs = 0;
    private long deliveredForDelayAvg = 0;

    /** Sliding window of delivery timestamps, used to compute a rolling throughput figure. */
    private final Deque<Long> recentDeliveryTimestamps = new ArrayDeque<>();
    private static final long THROUGHPUT_WINDOW_MS = 5000;

    private final List<PerformanceSnapshot> history = new ArrayList<>();
    private static final int MAX_HISTORY = 500;

    public void recordGenerated() {
        totalGenerated++;
    }

    public void recordDelivered(Packet packet, long nowMs) {
        totalDelivered++;
        sumEndToEndDelayMs += packet.endToEndDelayMs(nowMs);
        deliveredForDelayAvg++;
        recentDeliveryTimestamps.addLast(nowMs);
        pruneWindow(nowMs);
    }

    public void recordDropped() {
        totalDropped++;
    }

    private void pruneWindow(long nowMs) {
        while (!recentDeliveryTimestamps.isEmpty() && (nowMs - recentDeliveryTimestamps.peekFirst()) > THROUGHPUT_WINDOW_MS) {
            recentDeliveryTimestamps.pollFirst();
        }
    }

    public PerformanceSnapshot snapshot(NetworkTopology topology, long nowMs) {
        pruneWindow(nowMs);

        double avgDelay = deliveredForDelayAvg == 0 ? 0 : sumEndToEndDelayMs / deliveredForDelayAvg;
        double throughput = recentDeliveryTimestamps.size() / (THROUGHPUT_WINDOW_MS / 1000.0);

        long totalAttempted = totalDelivered + totalDropped;
        double pdr = totalAttempted == 0 ? 1.0 : (double) totalDelivered / totalAttempted;
        double lossRate = totalAttempted == 0 ? 0.0 : (double) totalDropped / totalAttempted;

        double avgRouterUtil = 0;
        int routerCount = 0;
        for (Router r : topology.getRouters()) {
            avgRouterUtil += r.getQueueOccupancy();
            routerCount++;
        }
        avgRouterUtil = routerCount == 0 ? 0 : avgRouterUtil / routerCount;

        double avgLinkUtil = 0;
        int linkCount = 0;
        for (Link l : topology.getLinks()) {
            avgLinkUtil += l.getUtilization();
            linkCount++;
        }
        avgLinkUtil = linkCount == 0 ? 0 : avgLinkUtil / linkCount;

        PerformanceSnapshot snap = new PerformanceSnapshot(nowMs, avgDelay, avgDelay, throughput,
                pdr, lossRate, avgRouterUtil, avgLinkUtil, totalGenerated, totalDelivered, totalDropped);

        history.add(snap);
        if (history.size() > MAX_HISTORY) history.remove(0);
        return snap;
    }

    public List<PerformanceSnapshot> getHistory() {
        return history;
    }

    public long getTotalGenerated() { return totalGenerated; }
    public long getTotalDelivered() { return totalDelivered; }
    public long getTotalDropped() { return totalDropped; }

    public void reset() {
        totalGenerated = 0;
        totalDelivered = 0;
        totalDropped = 0;
        sumEndToEndDelayMs = 0;
        deliveredForDelayAvg = 0;
        recentDeliveryTimestamps.clear();
        history.clear();
    }
}
