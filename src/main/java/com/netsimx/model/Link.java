package com.netsimx.model;

/**
 * A bidirectional communication link between two routers. Carries a cost
 * (used as edge weight by routing algorithms), a latency in milliseconds
 * (used to schedule packet arrival events), a bandwidth cap in packets/sec
 * (used by the congestion controller), and a link failure flag.
 */
public class Link {

    private final String id;
    private final String routerAId;
    private final String routerBId;
    private double cost;
    private double latencyMs;
    private double bandwidthPps; // packets per second capacity
    private boolean up = true;

    private long packetsCarried = 0;
    private double utilization = 0.0; // rolling estimate, 0..1

    /** Independent per-hop drop probability (0..1), simulating line noise/bit
     *  errors separate from congestion-caused buffer overflow. Applied once
     *  per packet crossing, in SimulationEngine. */
    private double lossProbability = 0.0;

    /** Set by "Congest Link" (context menu); remembers the pre-congestion
     *  bandwidth so it can be restored by "Release Congestion". */
    private boolean congested = false;
    private double preCongestionBandwidthPps = -1;

    public Link(String id, String routerAId, String routerBId, double cost, double latencyMs, double bandwidthPps) {
        this.id = id;
        this.routerAId = routerAId;
        this.routerBId = routerBId;
        this.cost = cost;
        this.latencyMs = latencyMs;
        this.bandwidthPps = bandwidthPps;
    }

    public String getId() { return id; }
    public String getRouterAId() { return routerAId; }
    public String getRouterBId() { return routerBId; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(double latencyMs) { this.latencyMs = latencyMs; }

    public double getBandwidthPps() { return bandwidthPps; }
    public void setBandwidthPps(double bandwidthPps) { this.bandwidthPps = bandwidthPps; }

    public double getLossProbability() { return lossProbability; }
    public void setLossProbability(double lossProbability) {
        this.lossProbability = Math.max(0, Math.min(1, lossProbability));
    }

    public boolean isCongested() { return congested; }

    /** Cuts bandwidth to a fraction of its normal value to simulate artificial congestion. */
    public void congest(double fractionOfNormal) {
        if (!congested) {
            preCongestionBandwidthPps = bandwidthPps;
            congested = true;
        }
        bandwidthPps = Math.max(1, preCongestionBandwidthPps * fractionOfNormal);
    }

    /** Restores the bandwidth that was in effect before {@link #congest}. */
    public void releaseCongestion() {
        if (congested && preCongestionBandwidthPps > 0) {
            bandwidthPps = preCongestionBandwidthPps;
        }
        congested = false;
        preCongestionBandwidthPps = -1;
    }

    public boolean isUp() { return up; }
    public void setUp(boolean up) { this.up = up; }

    public long getPacketsCarried() { return packetsCarried; }
    public double getUtilization() { return utilization; }

    /** Returns the router id on the other end of this link from {@code fromId}. */
    public String otherEnd(String fromId) {
        if (routerAId.equals(fromId)) return routerBId;
        if (routerBId.equals(fromId)) return routerAId;
        throw new IllegalArgumentException("Router " + fromId + " is not an endpoint of link " + id);
    }

    public boolean connects(String a, String b) {
        return (routerAId.equals(a) && routerBId.equals(b)) || (routerAId.equals(b) && routerBId.equals(a));
    }

    /** Record that a packet crossed this link this tick, and update a simple
     *  exponential moving average of utilization relative to capacity. */
    public void recordTraffic(double ticksPerSecond) {
        packetsCarried++;
        double instantaneous = bandwidthPps <= 0 ? 1.0 : Math.min(1.0, ticksPerSecond / bandwidthPps);
        utilization = (utilization * 0.9) + (instantaneous * 0.1);
    }

    public void decayUtilization() {
        utilization *= 0.95;
    }

    @Override
    public String toString() {
        return "Link{" + routerAId + "<->" + routerBId + ", cost=" + cost + ", up=" + up + "}";
    }
}
