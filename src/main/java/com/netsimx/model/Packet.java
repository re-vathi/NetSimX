package com.netsimx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single simulated packet traveling hop-by-hop across the network.
 * Carries enough metadata to compute end-to-end delay, throughput, and
 * packet-delivery-ratio statistics once it arrives (or is dropped/expires).
 */
public class Packet {

    public enum State { IN_TRANSIT, DELIVERED, DROPPED, EXPIRED }

    private final long id;
    private final String sourceId;
    private final String destinationId;
    private final long creationTimeMs;
    private final int sizeBytes;
    private int ttl;
    private final PacketPriority priority;
    private final Protocol protocol;

    /** Full path this packet is/was routed along, router IDs in order. */
    private List<String> path = new ArrayList<>();
    private int currentHopIndex = 0;

    private State state = State.IN_TRANSIT;
    private long deliveredAtMs = -1;

    /** For TCP: sequence number and whether this is a retransmission. */
    private long sequenceNumber = 0;
    private boolean retransmission = false;
    private boolean isAck = false;

    private static long nextId = 1;

    public static synchronized long allocateId() {
        return nextId++;
    }

    public Packet(String sourceId, String destinationId, int sizeBytes, int ttl,
                  PacketPriority priority, Protocol protocol, long creationTimeMs) {
        this.id = allocateId();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.sizeBytes = sizeBytes;
        this.ttl = ttl;
        this.priority = priority;
        this.protocol = protocol;
        this.creationTimeMs = creationTimeMs;
    }

    public long getId() { return id; }
    public String getSourceId() { return sourceId; }
    public String getDestinationId() { return destinationId; }
    public long getCreationTimeMs() { return creationTimeMs; }
    public int getSizeBytes() { return sizeBytes; }
    public int getTtl() { return ttl; }
    public PacketPriority getPriority() { return priority; }
    public Protocol getProtocol() { return protocol; }

    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; this.currentHopIndex = 0; }
    public int getCurrentHopIndex() { return currentHopIndex; }

    public String currentRouterId() {
        if (path.isEmpty() || currentHopIndex >= path.size()) return null;
        return path.get(currentHopIndex);
    }

    public String nextRouterId() {
        int next = currentHopIndex + 1;
        return (next < path.size()) ? path.get(next) : null;
    }

    /** Advance one hop; decrements TTL and returns false if TTL hit zero (packet should expire). */
    public boolean advanceHop() {
        currentHopIndex++;
        ttl--;
        return ttl > 0;
    }

    public boolean isAtDestination() {
        return currentRouterId() != null && currentRouterId().equals(destinationId);
    }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public long getDeliveredAtMs() { return deliveredAtMs; }
    public void markDelivered(long atMs) {
        this.state = State.DELIVERED;
        this.deliveredAtMs = atMs;
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public boolean isRetransmission() { return retransmission; }
    public void setRetransmission(boolean retransmission) { this.retransmission = retransmission; }
    public boolean isAck() { return isAck; }
    public void setAck(boolean ack) { isAck = ack; }

    public long endToEndDelayMs(long nowMs) {
        long end = (state == State.DELIVERED) ? deliveredAtMs : nowMs;
        return end - creationTimeMs;
    }

    @Override
    public String toString() {
        return String.format("Packet#%d[%s->%s, %s/%s, prio=%s, hop=%d/%d, state=%s]",
                id, sourceId, destinationId, protocol, isAck ? "ACK" : "DATA",
                priority, currentHopIndex, Math.max(0, path.size() - 1), state);
    }
}
