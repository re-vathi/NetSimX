package com.netsimx.simulation;

import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Packet;
import com.netsimx.model.PacketPriority;
import com.netsimx.model.Protocol;
import com.netsimx.model.Router;

import java.util.*;

/**
 * Generates network traffic modeled on common real-world application
 * profiles (Module 5). Each {@link TrafficType} maps to a packet size
 * range, QoS priority, transport protocol, and generation rate that
 * loosely mirrors the real thing (e.g. voice = small frequent packets,
 * high priority, UDP; file transfer = large packets, low priority, TCP).
 */
public class TrafficGenerator {

    public enum TrafficType {
        VOICE(PacketPriority.VOICE, Protocol.UDP, 60, 120, 50),      // ~160B every 20ms in reality; scaled for sim ticks
        VIDEO(PacketPriority.VIDEO, Protocol.UDP, 800, 1400, 30),
        WEB(PacketPriority.WEB, Protocol.TCP, 200, 1500, 15),
        EMAIL(PacketPriority.EMAIL, Protocol.TCP, 300, 2000, 5),
        FILE_TRANSFER(PacketPriority.FILE_TRANSFER, Protocol.TCP, 1400, 1500, 40);

        final PacketPriority priority;
        final Protocol protocol;
        final int minSize;
        final int maxSize;
        /** Relative generation weight used when a mixed traffic mode is selected. */
        final int weight;

        TrafficType(PacketPriority priority, Protocol protocol, int minSize, int maxSize, int weight) {
            this.priority = priority;
            this.protocol = protocol;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.weight = weight;
        }
    }

    /** A configured source->destination traffic flow the generator will keep producing packets for. */
    public static class Flow {
        public final String sourceId;
        public final String destinationId;
        public final TrafficType type;
        /** Probability [0,1] that a packet is generated for this flow on any given tick. */
        public final double packetsPerTickProbability;

        public Flow(String sourceId, String destinationId, TrafficType type, double packetsPerTickProbability) {
            this.sourceId = sourceId;
            this.destinationId = destinationId;
            this.type = type;
            this.packetsPerTickProbability = packetsPerTickProbability;
        }
    }

    private final List<Flow> flows = new ArrayList<>();
    private final PacketGenerator packetGenerator = new PacketGenerator();
    private final Random random = new Random();
    private static final int DEFAULT_TTL = 32;

    public void addFlow(Flow flow) {
        flows.add(flow);
    }

    public void removeFlow(Flow flow) {
        flows.remove(flow);
    }

    public List<Flow> getFlows() {
        return Collections.unmodifiableList(flows);
    }

    public void clearFlows() {
        flows.clear();
    }

    /** Add a random mixed-traffic flow between two arbitrary distinct UP routers. */
    public Flow addRandomFlow(NetworkTopology topology) {
        List<Router> upRouters = new ArrayList<>();
        for (Router r : topology.getRouters()) if (r.isUp()) upRouters.add(r);
        if (upRouters.size() < 2) return null;

        Router src = upRouters.get(random.nextInt(upRouters.size()));
        Router dst;
        do {
            dst = upRouters.get(random.nextInt(upRouters.size()));
        } while (dst == src);

        TrafficType type = weightedRandomType();
        Flow flow = new Flow(src.getId(), dst.getId(), type, 0.15 + random.nextDouble() * 0.25);
        flows.add(flow);
        return flow;
    }

    private TrafficType weightedRandomType() {
        int totalWeight = 0;
        for (TrafficType t : TrafficType.values()) totalWeight += t.weight;
        int r = random.nextInt(totalWeight);
        int cumulative = 0;
        for (TrafficType t : TrafficType.values()) {
            cumulative += t.weight;
            if (r < cumulative) return t;
        }
        return TrafficType.WEB;
    }

    /** Called once per simulation tick; probabilistically emits packets for each configured flow. */
    public List<Packet> generateForTick(long nowMs) {
        List<Packet> generated = new ArrayList<>();
        for (Flow flow : flows) {
            if (random.nextDouble() < flow.packetsPerTickProbability) {
                int size = flow.type.minSize + random.nextInt(Math.max(1, flow.type.maxSize - flow.type.minSize));
                Packet p = packetGenerator.create(flow.sourceId, flow.destinationId, size, DEFAULT_TTL,
                        flow.type.priority, flow.type.protocol, nowMs);
                generated.add(p);
            }
        }
        return generated;
    }
}
