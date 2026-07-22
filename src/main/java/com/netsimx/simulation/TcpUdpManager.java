package com.netsimx.simulation;

import com.netsimx.model.Packet;
import com.netsimx.model.Protocol;

import java.util.*;

/**
 * Models the reliability difference between TCP and UDP (Module 8).
 *
 * - UDP packets are fire-and-forget: delivery or loss is final, no ACK,
 *   no retransmission - handled entirely by the normal packet lifecycle
 *   elsewhere and this class does nothing for them.
 * - TCP packets are tracked here from the moment they're sent until
 *   acknowledged. If a TCP data packet is dropped, or an ACK isn't seen
 *   within {@link #ackTimeoutMs}, the original payload is retransmitted
 *   (flagged via {@link Packet#setRetransmission}) up to
 *   {@link #maxRetransmissions} times before giving up.
 */
public class TcpUdpManager {

    public static class PendingTcp {
        final Packet original;
        final long sentAtMs;
        int attempts;

        PendingTcp(Packet original, long sentAtMs, int attempts) {
            this.original = original;
            this.sentAtMs = sentAtMs;
            this.attempts = attempts;
        }
    }

    private final Map<Long, PendingTcp> awaitingAck = new LinkedHashMap<>();
    private long ackTimeoutMs = 2000;
    private int maxRetransmissions = 3;

    private long retransmissionCount = 0;
    private long ackCount = 0;
    private long tcpGiveUpCount = 0;

    public void setAckTimeoutMs(long ackTimeoutMs) { this.ackTimeoutMs = ackTimeoutMs; }
    public void setMaxRetransmissions(int maxRetransmissions) { this.maxRetransmissions = maxRetransmissions; }

    public long getRetransmissionCount() { return retransmissionCount; }
    public long getAckCount() { return ackCount; }
    public long getTcpGiveUpCount() { return tcpGiveUpCount; }
    public int getPendingCount() { return awaitingAck.size(); }

    /** Call whenever a fresh (non-ACK) TCP packet is handed to the network. */
    public void onTcpPacketSent(Packet packet, long nowMs) {
        if (packet.getProtocol() != Protocol.TCP || packet.isAck()) return;
        awaitingAck.put(packet.getId(), new PendingTcp(packet, nowMs, 1));
    }

    /**
     * Call when a packet is delivered to its destination. If it's a TCP
     * data packet, synthesizes the ACK to send back; if it's the ACK
     * itself arriving at the original sender, clears the pending entry.
     * Returns the ACK packet to inject into the network, or null if none
     * is needed (UDP, or this delivered packet already was an ACK).
     */
    public Packet onDelivered(Packet packet, long nowMs) {
        if (packet.getProtocol() != Protocol.TCP) return null;

        if (packet.isAck()) {
            awaitingAck.remove(packet.getSequenceNumber());
            ackCount++;
            return null;
        }

        // Data packet delivered -> build and return an ACK addressed back to the sender.
        Packet ack = new Packet(packet.getDestinationId(), packet.getSourceId(),
                40, 32, packet.getPriority(), Protocol.TCP, nowMs);
        ack.setAck(true);
        ack.setSequenceNumber(packet.getId());
        return ack;
    }

    /**
     * Call when a TCP data packet is dropped (congestion or link failure).
     * Returns a retransmitted copy to re-inject, or null if the max retry
     * count has been exhausted (connection gives up, mirrors real TCP
     * closing the connection after repeated failures).
     */
    public Packet onDropped(Packet packet, long nowMs) {
        if (packet.getProtocol() != Protocol.TCP || packet.isAck()) return null;

        PendingTcp pending = awaitingAck.remove(packet.getId());
        int attempts = (pending != null) ? pending.attempts : 1;
        if (attempts >= maxRetransmissions) {
            tcpGiveUpCount++;
            return null;
        }

        Packet retry = new Packet(packet.getSourceId(), packet.getDestinationId(),
                packet.getSizeBytes(), 32, packet.getPriority(), Protocol.TCP, nowMs);
        retry.setRetransmission(true);
        retransmissionCount++;
        awaitingAck.put(retry.getId(), new PendingTcp(retry, nowMs, attempts + 1));
        return retry;
    }

    /**
     * Call once per tick: scans for TCP packets that have been awaiting an
     * ACK longer than {@link #ackTimeoutMs} and returns retransmissions
     * for them (or gives up past {@link #maxRetransmissions}).
     */
    public List<Packet> checkTimeouts(long nowMs) {
        List<Packet> retries = new ArrayList<>();
        List<Long> expired = new ArrayList<>();

        for (var entry : awaitingAck.entrySet()) {
            PendingTcp pending = entry.getValue();
            if (nowMs - pending.sentAtMs < ackTimeoutMs) continue;
            expired.add(entry.getKey());

            if (pending.attempts >= maxRetransmissions) {
                tcpGiveUpCount++;
                continue;
            }
            Packet retry = new Packet(pending.original.getSourceId(), pending.original.getDestinationId(),
                    pending.original.getSizeBytes(), 32, pending.original.getPriority(), Protocol.TCP, nowMs);
            retry.setRetransmission(true);
            retransmissionCount++;
            awaitingAck.put(retry.getId(), new PendingTcp(retry, nowMs, pending.attempts + 1));
            retries.add(retry);
        }
        for (Long id : expired) awaitingAck.remove(id);
        return retries;
    }

    public void reset() {
        awaitingAck.clear();
        retransmissionCount = 0;
        ackCount = 0;
        tcpGiveUpCount = 0;
    }
}
