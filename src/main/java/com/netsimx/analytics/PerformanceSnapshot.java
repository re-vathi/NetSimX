package com.netsimx.analytics;

/** Immutable snapshot of network-wide performance metrics at one point in simulated time. */
public class PerformanceSnapshot {

    public final long simTimeMs;
    public final double avgLatencyMs;
    public final double avgEndToEndDelayMs;
    public final double throughputPacketsPerSec;
    public final double packetDeliveryRatio;   // 0..1
    public final double packetLossRate;        // 0..1
    public final double avgRouterUtilization;  // 0..1 average queue occupancy
    public final double avgLinkUtilization;    // 0..1
    public final long totalPacketsGenerated;
    public final long totalPacketsDelivered;
    public final long totalPacketsDropped;

    public PerformanceSnapshot(long simTimeMs, double avgLatencyMs, double avgEndToEndDelayMs,
                                double throughputPacketsPerSec, double packetDeliveryRatio,
                                double packetLossRate, double avgRouterUtilization, double avgLinkUtilization,
                                long totalPacketsGenerated, long totalPacketsDelivered, long totalPacketsDropped) {
        this.simTimeMs = simTimeMs;
        this.avgLatencyMs = avgLatencyMs;
        this.avgEndToEndDelayMs = avgEndToEndDelayMs;
        this.throughputPacketsPerSec = throughputPacketsPerSec;
        this.packetDeliveryRatio = packetDeliveryRatio;
        this.packetLossRate = packetLossRate;
        this.avgRouterUtilization = avgRouterUtilization;
        this.avgLinkUtilization = avgLinkUtilization;
        this.totalPacketsGenerated = totalPacketsGenerated;
        this.totalPacketsDelivered = totalPacketsDelivered;
        this.totalPacketsDropped = totalPacketsDropped;
    }

    public String toCsvRow() {
        return String.format("%d,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d",
                simTimeMs, avgLatencyMs, avgEndToEndDelayMs, throughputPacketsPerSec,
                packetDeliveryRatio, packetLossRate, avgRouterUtilization, avgLinkUtilization,
                totalPacketsGenerated, totalPacketsDelivered, totalPacketsDropped);
    }

    public static String csvHeader() {
        return "simTimeMs,avgLatencyMs,avgEndToEndDelayMs,throughputPps,packetDeliveryRatio," +
                "packetLossRate,avgRouterUtilization,avgLinkUtilization,totalGenerated,totalDelivered,totalDropped";
    }
}
