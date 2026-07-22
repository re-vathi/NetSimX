package com.netsimx.simulation;

import com.netsimx.model.Packet;
import com.netsimx.model.PacketPriority;
import com.netsimx.model.Protocol;

/** Low-level factory for constructing {@link Packet} instances with sane defaults. */
public class PacketGenerator {

    public Packet create(String sourceId, String destinationId, int sizeBytes, int ttl,
                          PacketPriority priority, Protocol protocol, long nowMs) {
        return new Packet(sourceId, destinationId, sizeBytes, ttl, priority, protocol, nowMs);
    }
}
