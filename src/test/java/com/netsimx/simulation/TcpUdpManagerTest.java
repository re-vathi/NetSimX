package com.netsimx.simulation;

import com.netsimx.model.Packet;
import com.netsimx.model.PacketPriority;
import com.netsimx.model.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TcpUdpManagerTest {

    /**
     * Regression test for a ConcurrentModificationException that occurred
     * when multiple TCP packets timed out in the same {@code checkTimeouts}
     * call: the original implementation called {@code awaitingAck.put(...)}
     * for each retry while still iterating {@code awaitingAck.entrySet()}.
     * Reproducing needs at least two simultaneously-expired entries so the
     * for-each loop is still mid-iteration when a mutation would occur.
     */
    @Test
    void multipleSimultaneousTimeoutsDoNotThrowConcurrentModification() {
        TcpUdpManager manager = new TcpUdpManager();
        manager.setAckTimeoutMs(100);
        manager.setMaxRetransmissions(3);

        long t0 = 1000;
        for (int i = 0; i < 5; i++) {
            Packet p = new Packet("R1", "R2", 500, 32, PacketPriority.WEB, Protocol.TCP, t0);
            manager.onTcpPacketSent(p, t0);
        }

        assertDoesNotThrow(() -> manager.checkTimeouts(t0 + 200),
                "checkTimeouts must not throw when multiple entries expire in the same call");
    }

    @Test
    void expiredPacketsAreRetransmittedUpToMaxAttempts() {
        TcpUdpManager manager = new TcpUdpManager();
        manager.setAckTimeoutMs(100);
        manager.setMaxRetransmissions(2);

        Packet p = new Packet("R1", "R2", 500, 32, PacketPriority.WEB, Protocol.TCP, 0);
        manager.onTcpPacketSent(p, 0);

        // Original send counts as attempt 1; first timeout produces one retry (attempt 2).
        var firstRetry = manager.checkTimeouts(200);
        assertEquals(1, firstRetry.size());
        assertTrue(firstRetry.get(0).isRetransmission());

        // The engine re-registers every admitted packet (including retries) via
        // onTcpPacketSent - this must NOT reset the attempt count back to 1
        // (that was the second bug this test caught: see TcpUdpManager.onTcpPacketSent's javadoc).
        manager.onTcpPacketSent(firstRetry.get(0), 200);

        // attempts is now 2, equal to maxRetransmissions - the next timeout should give up, not retry again.
        var secondCheck = manager.checkTimeouts(400);
        assertEquals(0, secondCheck.size(), "Should give up once attempts reaches maxRetransmissions");
        assertEquals(1, manager.getTcpGiveUpCount());
        assertEquals(1, manager.getRetransmissionCount(), "Exactly one retransmission should have occurred");
    }

    @Test
    void udpPacketsAreNeverTrackedForRetransmission() {
        TcpUdpManager manager = new TcpUdpManager();
        manager.setAckTimeoutMs(100);

        Packet udp = new Packet("R1", "R2", 200, 32, PacketPriority.VOICE, Protocol.UDP, 0);
        manager.onTcpPacketSent(udp, 0); // should be a no-op for UDP

        assertEquals(0, manager.getPendingCount());
        assertTrue(manager.checkTimeouts(1000).isEmpty());
    }
}
