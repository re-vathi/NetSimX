package com.netsimx.simulation;

import com.netsimx.model.Packet;
import com.netsimx.model.PacketPriority;
import com.netsimx.model.Router;

import java.util.*;

/**
 * Implements strict-priority Quality of Service scheduling: at every
 * router, when there is more traffic ready to forward than link capacity
 * allows for this tick, higher-priority classes (Voice &gt; Video &gt; Web
 * &gt; Email &gt; File Transfer - see {@link PacketPriority} ordinal order)
 * are selected first.
 */
public class QoSScheduler {

    /**
     * Given all packets currently queued at a router and a budget of how
     * many can actually be transmitted this tick (bounded by outgoing link
     * bandwidth), returns the subset to transmit now, in priority order.
     * The rest remain queued for a future tick.
     */
    public List<Packet> selectForTransmission(Router router, int transmitBudget) {
        List<Packet> all = router.drainAll();
        if (all.isEmpty() || transmitBudget <= 0) {
            // nothing to send this tick (or nothing pending) - put back untouched
            for (Packet p : all) router.enqueue(p);
            return List.of();
        }

        all.sort(Comparator.comparingInt((Packet p) -> p.getPriority().ordinal())
                .thenComparingLong(Packet::getCreationTimeMs)); // FIFO within same priority

        List<Packet> selected = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (i < transmitBudget) {
                selected.add(all.get(i));
            } else {
                router.enqueue(all.get(i)); // re-queue anything over budget for next tick
            }
        }
        return selected;
    }

    /** Human-readable priority ranking, for the GUI's QoS legend. */
    public List<PacketPriority> priorityOrder() {
        return List.of(PacketPriority.values());
    }
}
