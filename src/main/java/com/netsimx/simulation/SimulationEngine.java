package com.netsimx.simulation;

import com.netsimx.analytics.StatisticsCollector;
import com.netsimx.model.*;
import com.netsimx.routing.DijkstraRouting;
import com.netsimx.routing.ECMPRouting;
import com.netsimx.routing.RoutingAlgorithm;
import com.netsimx.routing.RoutingTable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The central tick-driven simulation loop. Ownership map:
 *
 * <pre>
 *  NetworkTopology      - graph state (routers/links)                [Module 1]
 *  TrafficGenerator     - produces new packets each tick               [Module 5]
 *  RoutingTable         - cached shortest paths, per selected algorithm[Module 3]
 *  QoSScheduler         - picks which queued packets go out this tick  [Module 6]
 *  CongestionController - per-link per-tick transmit budget            [Module 4]
 *  QueueManager         - congestion metric aggregation                [Module 4]
 *  FailureSimulator     - link/router up/down injection                [Module 7]
 *  TcpUdpManager        - ACK/retransmit vs fire-and-forget behavior   [Module 8]
 *  StatisticsCollector  - rolling performance metrics                  [Module 11]
 * </pre>
 *
 * Each call to {@link #tick()} advances simulated time by
 * {@code tickIntervalMs} and performs one full generate -&gt; schedule -&gt;
 * forward -&gt; deliver/drop pass. The GUI (or a headless driver, e.g. for
 * batch experiments / tests) is expected to call tick() on a timer, or in
 * a tight loop for offline analysis.
 */
public class SimulationEngine {

    private final NetworkTopology topology;
    private final TrafficGenerator trafficGenerator = new TrafficGenerator();
    private final QoSScheduler qosScheduler = new QoSScheduler();
    private final QueueManager queueManager = new QueueManager();
    private final FailureSimulator failureSimulator = new FailureSimulator();
    private final TcpUdpManager tcpUdpManager = new TcpUdpManager();
    private final StatisticsCollector statistics = new StatisticsCollector();

    private RoutingTable routingTable;
    private CongestionController congestionController;

    private final List<SimulationListener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private long simTimeMs = 0;
    private long tickIntervalMs = 100; // simulated ms per tick
    private double ticksPerSecond = 1000.0 / tickIntervalMs;
    private boolean running = false;

    /** Packets currently known to the engine, keyed by ID, so the GUI can look up state for animation. */
    private final Map<Long, Packet> activePackets = new LinkedHashMap<>();
    private static final int MAX_TTL_DEFAULT = 32;

    public SimulationEngine(NetworkTopology topology) {
        this.topology = topology;
        this.routingTable = new RoutingTable(new DijkstraRouting());
        this.congestionController = new CongestionController(ticksPerSecond);
    }

    // ------------------------------------------------------------------ //
    // Configuration / accessors
    // ------------------------------------------------------------------ //

    public NetworkTopology getTopology() { return topology; }
    public TrafficGenerator getTrafficGenerator() { return trafficGenerator; }
    public QueueManager getQueueManager() { return queueManager; }
    public FailureSimulator getFailureSimulator() { return failureSimulator; }
    public TcpUdpManager getTcpUdpManager() { return tcpUdpManager; }
    public StatisticsCollector getStatistics() { return statistics; }
    public RoutingTable getRoutingTable() { return routingTable; }
    public long getSimTimeMs() { return simTimeMs; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public void setTickIntervalMs(long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
        this.ticksPerSecond = 1000.0 / tickIntervalMs;
        this.congestionController = new CongestionController(ticksPerSecond);
    }
    public long getTickIntervalMs() { return tickIntervalMs; }

    public void setRoutingAlgorithm(RoutingAlgorithm algorithm) {
        this.routingTable = new RoutingTable(algorithm);
        recomputeRoutes();
    }
    public RoutingAlgorithm getRoutingAlgorithm() { return routingTable.getAlgorithm(); }

    public void addListener(SimulationListener listener) { listeners.add(listener); }
    public void removeListener(SimulationListener listener) { listeners.remove(listener); }

    public Collection<Packet> getActivePackets() { return activePackets.values(); }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    public void reset() {
        simTimeMs = 0;
        activePackets.clear();
        statistics.reset();
        queueManager.reset();
        tcpUdpManager.reset();
        for (Router r : topology.getRouters()) {
            r.drainAll();
            r.resetStats();
        }
        recomputeRoutes();
    }

    public void recomputeRoutes() {
        routingTable.recompute(topology, simTimeMs);
        for (SimulationListener l : listeners) l.onRoutesRecomputed(simTimeMs);
    }

    // ------------------------------------------------------------------ //
    // Topology mutation helpers (routed through here so routes auto-recompute)
    // ------------------------------------------------------------------ //

    public void setLinkUp(Link link, boolean up) {
        if (up) failureSimulator.recoverLink(link, this::notifyLinkStatus);
        else failureSimulator.failLink(link, this::notifyLinkStatus);
        recomputeRoutes();
    }

    public void setRouterUp(Router router, boolean up) {
        if (up) failureSimulator.recoverRouter(router, this::notifyRouterStatus);
        else failureSimulator.failRouter(router, this::notifyRouterStatus);
        recomputeRoutes();
    }

    private void notifyLinkStatus(Link link, boolean up) {
        for (SimulationListener l : listeners) l.onLinkStatusChanged(link, up);
        log((up ? "Link UP: " : "Link DOWN: ") + link.getRouterAId() + " <-> " + link.getRouterBId());
    }

    private void notifyRouterStatus(Router router, boolean up) {
        for (SimulationListener l : listeners) l.onRouterStatusChanged(router, up);
        log((up ? "Router UP: " : "Router DOWN: ") + router.getId());
    }

    private void log(String msg) {
        for (SimulationListener l : listeners) l.onLog(msg);
    }

    // ------------------------------------------------------------------ //
    // Main tick
    // ------------------------------------------------------------------ //

    public void tick() {
        simTimeMs += tickIntervalMs;

        // Module 7: chaos-mode random failure injection
        Link failed = failureSimulator.maybeInjectRandomFailure(topology, this::notifyLinkStatus);
        if (failed != null) {
            recomputeRoutes();
        }

        generateNewTraffic();
        handleTcpTimeouts();
        forwardQueuedPackets();

        queueManager.sample(topology);
        statistics.snapshot(topology, simTimeMs);

        for (SimulationListener l : listeners) l.onTick(simTimeMs);
    }

    // ------------------------------------------------------------------ //
    // Traffic generation
    // ------------------------------------------------------------------ //

    private void generateNewTraffic() {
        for (Packet packet : trafficGenerator.generateForTick(simTimeMs)) {
            admitPacket(packet);
        }
    }

    /** Compute a route and, if one exists, enqueue the packet at its source router. Drops it otherwise. */
    private boolean admitPacket(Packet packet) {
        Optional<RoutingAlgorithm.RouteResult> route = resolveRoute(packet.getSourceId(), packet.getDestinationId());
        if (route.isEmpty()) {
            statistics.recordDropped();
            for (SimulationListener l : listeners) l.onPacketDropped(packet, "no route to destination");
            return false;
        }

        packet.setPath(route.get().getPath());
        statistics.recordGenerated();
        activePackets.put(packet.getId(), packet);
        for (SimulationListener l : listeners) l.onPacketCreated(packet);

        Router source = topology.getRouter(packet.getSourceId());
        if (source == null || !source.enqueue(packet)) {
            statistics.recordDropped();
            packet.setState(Packet.State.DROPPED);
            activePackets.remove(packet.getId());
            for (SimulationListener l : listeners) l.onPacketDropped(packet, "source queue full");
            return false;
        }

        if (packet.getProtocol() == Protocol.TCP && !packet.isAck()) {
            tcpUdpManager.onTcpPacketSent(packet, simTimeMs);
        }
        return true;
    }

    /**
     * Resolves a route from the cached routing table, except for ECMP
     * where we deliberately recompute live so each new packet can land on
     * a different equal-cost path (Module 9 load balancing) rather than
     * every flow sticking to whatever the last topology-change recompute
     * happened to cache.
     */
    private Optional<RoutingAlgorithm.RouteResult> resolveRoute(String sourceId, String destinationId) {
        if (routingTable.getAlgorithm() instanceof ECMPRouting) {
            Map<String, RoutingAlgorithm.RouteResult> live = routingTable.getAlgorithm().computeRoutes(topology, sourceId);
            return Optional.ofNullable(live.get(destinationId));
        }
        return routingTable.lookup(sourceId, destinationId);
    }

    private void handleTcpTimeouts() {
        for (Packet retry : tcpUdpManager.checkTimeouts(simTimeMs)) {
            log("TCP retransmission (timeout) for flow " + retry.getSourceId() + "->" + retry.getDestinationId());
            admitPacket(retry);
        }
    }

    // ------------------------------------------------------------------ //
    // Forwarding
    // ------------------------------------------------------------------ //

    private void forwardQueuedPackets() {
        Map<String, Integer> linkSentThisTick = new HashMap<>();

        for (Router router : topology.getRouters()) {
            if (!router.isUp() || !router.hasPending()) continue;

            int totalBudget = 0;
            for (Link link : topology.incidentLinks(router.getId())) {
                if (link.isUp()) totalBudget += congestionController.transmitBudgetForTick(link);
            }

            List<Packet> selected = qosScheduler.selectForTransmission(router, totalBudget);

            for (Packet packet : selected) {
                forwardOnePacket(packet, router, linkSentThisTick);
            }
        }

        for (Link link : topology.getLinks()) {
            int sent = linkSentThisTick.getOrDefault(link.getId(), 0);
            congestionController.afterTick(link, sent);
        }
    }

    private void forwardOnePacket(Packet packet, Router fromRouter, Map<String, Integer> linkSentThisTick) {
        String nextHopId = packet.nextRouterId();
        if (nextHopId == null) {
            // Already at path's end but somehow still queued - treat as delivered defensively.
            deliverPacket(packet);
            return;
        }

        Link link = topology.findLinkBetween(fromRouter.getId(), nextHopId).orElse(null);
        if (link == null || !link.isUp()) {
            dropPacket(packet, "link down: " + fromRouter.getId() + "<->" + nextHopId);
            return;
        }

        int budget = congestionController.transmitBudgetForTick(link);
        int already = linkSentThisTick.getOrDefault(link.getId(), 0);
        if (already >= budget) {
            // Link saturated this tick - put the packet back for next tick rather than dropping it outright.
            fromRouter.enqueuePriority(packet);
            return;
        }
        linkSentThisTick.merge(link.getId(), 1, Integer::sum);

        if (link.getLossProbability() > 0 && random.nextDouble() < link.getLossProbability()) {
            dropPacket(packet, String.format("line error on %s (p=%.0f%%)", link.getId(), link.getLossProbability() * 100));
            return;
        }

        boolean ttlOk = packet.advanceHop();
        if (!ttlOk) {
            dropPacket(packet, "TTL expired");
            return;
        }

        for (SimulationListener l : listeners) l.onPacketHop(packet, fromRouter.getId(), nextHopId, link);

        if (packet.isAtDestination()) {
            deliverPacket(packet);
            return;
        }

        Router nextRouter = topology.getRouter(nextHopId);
        if (nextRouter == null || !nextRouter.isUp() || !nextRouter.enqueue(packet)) {
            dropPacket(packet, "buffer overflow at " + nextHopId);
            return;
        }
    }

    private void deliverPacket(Packet packet) {
        packet.markDelivered(simTimeMs);
        statistics.recordDelivered(packet, simTimeMs);
        activePackets.remove(packet.getId());
        for (SimulationListener l : listeners) l.onPacketDelivered(packet);

        if (packet.getProtocol() == Protocol.TCP) {
            Packet ack = tcpUdpManager.onDelivered(packet, simTimeMs);
            if (ack != null) {
                admitPacket(ack);
            }
        }
    }

    private void dropPacket(Packet packet, String reason) {
        packet.setState(Packet.State.DROPPED);
        statistics.recordDropped();
        activePackets.remove(packet.getId());
        for (SimulationListener l : listeners) l.onPacketDropped(packet, reason);

        if (packet.getProtocol() == Protocol.TCP && !packet.isAck()) {
            Packet retry = tcpUdpManager.onDropped(packet, simTimeMs);
            if (retry != null) {
                log("TCP retransmission (loss) for flow " + retry.getSourceId() + "->" + retry.getDestinationId());
                admitPacket(retry);
            } else {
                log("TCP connection gave up: " + packet.getSourceId() + "->" + packet.getDestinationId());
            }
        }
    }
}
