# NetSimX — Intelligent Network Routing & Traffic Simulator

A Java 21 + JavaFX simulator that models real computer networks: routers,
links, packet-by-packet forwarding, congestion, QoS scheduling, TCP/UDP
behavior, link/router failures, live performance analytics, and an
adaptive AI route optimizer — all visualized in an interactive dashboard.

![NetSimX dashboard](docs/screenshot.png)

This build was verified end-to-end during development: every package
compiles cleanly, the full JUnit test suite passes, and the JavaFX
dashboard was launched and screenshotted headlessly to confirm it actually
renders (not just compiles) before being handed off.

## Quick start

```bash
mvn javafx:run      # launch the dashboard (fetches JavaFX from Maven Central on first run)
mvn test             # run the JUnit 5 test suite
mvn package          # build a distributable fat jar at target/netsimx.jar
java -jar target/netsimx.jar
```

Requires **JDK 21+** and an internet connection on first build (Maven
needs to download JavaFX and JUnit from Maven Central — everything else in
this project is dependency-free, see [Design notes](#design-notes)).

On launch, NetSimX loads `config/sample-network.json` (a 9-router, 3-tier
campus-style topology) if it's found in the working directory; otherwise
it falls back to a small built-in 4-router demo topology, so the app never
fails to start.

## What's implemented

| # | Module (from the design doc) | Where |
|---|---|---|
| 1 | Network Topology Builder | `model.NetworkTopology`, `gui.TopologyCanvas` (drag routers, click-add router/link, right-click fail) |
| 2 | Packet Simulation Engine | `model.Packet`, `simulation.PacketGenerator`, `simulation.SimulationEngine` |
| 3 | Routing Engine | `routing.DijkstraRouting`, `routing.BellmanFordRouting`, `routing.ECMPRouting`, `routing.RoutingTable` |
| 4 | Congestion & Queue Management | `simulation.QueueManager`, `simulation.CongestionController`, bounded queues on `model.Router` |
| 5 | Traffic Generator | `simulation.TrafficGenerator` (Voice/Video/Web/Email/File Transfer profiles) |
| 6 | Quality of Service (QoS) | `simulation.QoSScheduler` (strict priority scheduling) |
| 7 | Network Failure Simulation | `simulation.FailureSimulator` (manual + chaos-mode random failures, auto-reroute) |
| 8 | TCP & UDP Communication | `simulation.TcpUdpManager` (ACK/retransmit/timeout vs fire-and-forget) |
| 9 | Load Balancing (ECMP) | `routing.ECMPRouting` (per-packet path selection across equal-cost routes) |
| 10 | AI-Based Route Optimization | `ai.QLearningRouteOptimizer` (see note below) |
| 11 | Performance Analytics | `analytics.StatisticsCollector`, `analytics.PerformanceSnapshot` |
| 12 | Interactive Dashboard | `gui.NetSimXApp`, `gui.TopologyCanvas`, `gui.ChartsPanel`, `gui.ControlPanel`, `gui.LogConsole` |
| 13 | Digital Twin Support | `persistence.TopologyIO` (JSON import/export), `config/sample-network.json` |

## Design notes

**AI route optimizer is pure Java, not Python/Stable-Baselines3.** The
original design doc listed Python + Stable-Baselines3 as an optional
external RL component. This sandbox couldn't reach PyPI or a Python RL
stack in a way that would cleanly integrate with a JavaFX desktop app, so
`ai.QLearningRouteOptimizer` implements a real (if intentionally
lightweight) tabular Q-learning agent directly in Java — no external
service, no stub. It learns a state/action table keyed on
`(currentRouter, destination)` and adapts its routing decisions to live
congestion (queue occupancy) and link utilization, using an actual
Bellman-equation update with epsilon-greedy exploration. It implements the
same `RoutingAlgorithm` interface as Dijkstra/Bellman-Ford/ECMP, so it's a
drop-in, selectable option in the dashboard's algorithm dropdown, and you
can toggle "Train AI optimizer continuously" to watch it adapt in real
time. Swapping in an external Python/Stable-Baselines3 service later would
mean replacing this one class with an HTTP/gRPC client while keeping the
same interface — nothing else in the app would need to change.

**No SQLite dependency.** The design doc's tech stack mentions SQLite for
persistence. To keep the project's only external dependencies as JavaFX
(+ JUnit for tests), topology import/export uses a small hand-rolled JSON
reader/writer (`persistence.MiniJson`) instead of a JSON library, and
performance history exports to CSV (`persistence.CsvExporter`) instead of
a database — both are genuinely useful (JSON topologies are
portable/diffable; CSV opens directly in Excel/pandas) and avoid pulling
in a JDBC driver for what is, in this simulator, fundamentally
append-only time-series data. If you want actual SQLite-backed
persistence, add `org.xerial:sqlite-jdbc` to `pom.xml` and swap
`CsvExporter` for a small JDBC writer — `StatisticsCollector`'s
`PerformanceSnapshot` objects already have everything a row needs.

## Using the dashboard

- **Start / Pause / Step / Reset** — top-left controls. Step lets you
  advance one tick at a time while paused, useful for watching routing
  decisions closely.
- **Routing Algorithm dropdown** — switch live between Dijkstra,
  Bellman-Ford, ECMP, and the AI optimizer. Routes recompute immediately.
- **Tick interval slider** — controls simulated time per tick (20–1000ms);
  lower = faster simulation.
- **Topology editing** — toggle "+ Router" and click empty canvas space to
  add a router; toggle "+ Link" and click two routers in sequence to
  connect them; click a router or link then "Remove Selected" to delete
  it. Drag routers to reposition them.
- **Right-click a router or link** to toggle it up/down — this is the
  quickest way to test rerouting after a failure (Module 7).
- **Traffic Generator panel** — add a flow by router ID + traffic type, or
  click "Add Random Flow" to wire up two random active routers. Flows keep
  generating packets every tick per their configured rate.
- **Chaos mode** — check the box and drag the slider to have the
  simulator randomly fail links on its own, for unattended resilience
  testing.
- **Load JSON / Save JSON** — import/export the current topology using
  the format in `config/sample-network.json`.
- **Export Stats CSV** — dump the full performance history collected so
  far for offline analysis.
- **Live Charts / Event Log tabs** (right side) — throughput, delay, PDR,
  and utilization update every tick; the log records drops, retransmits,
  failures, and recoveries as they happen.

## Project layout

```
netsimx/
├── pom.xml
├── config/
│   └── sample-network.json      # 9-router demo topology loaded on startup
├── docs/
│   └── screenshot.png
└── src/
    ├── main/java/com/netsimx/
    │   ├── model/                # Router, Link, Packet, NetworkTopology, enums
    │   ├── routing/               # Dijkstra, Bellman-Ford, ECMP, RoutingTable
    │   ├── simulation/            # SimulationEngine + all Module 2/4/5/6/7/8 logic
    │   ├── ai/                    # Q-learning route optimizer
    │   ├── analytics/             # StatisticsCollector, PerformanceSnapshot
    │   ├── persistence/           # MiniJson, TopologyIO, CsvExporter
    │   └── gui/                   # NetSimXApp + dashboard components
    ├── main/resources/com/netsimx/gui/
    │   └── dashboard.css
    └── test/java/com/netsimx/
        ├── routing/RoutingAlgorithmsTest.java     # JUnit 5, passing
        ├── simulation/SimulationEngineTest.java   # JUnit 5, passing
        └── ManualSmokeCheck.java                  # plain `java`-runnable end-to-end check
```

`ManualSmokeCheck` is a standalone `main()`-based sanity check (not picked
up by Surefire) you can run directly for a quick, dependency-free
end-to-end confidence check without needing `mvn test`:

```bash
javac -d target/classes $(find src/main/java -name "*.java")
javac -d target/test-classes -cp target/classes src/test/java/com/netsimx/ManualSmokeCheck.java
java -cp target/classes:target/test-classes com.netsimx.ManualSmokeCheck
```

## How the simulation loop works

Each tick (`SimulationEngine.tick()`):

1. **Chaos check** — maybe fail a random link (if chaos mode is on).
2. **Traffic generation** — each configured flow probabilistically emits a
   packet; a route is resolved (cached for Dijkstra/Bellman-Ford, computed
   fresh per-packet for ECMP so load actually balances across equal-cost
   paths) and the packet is enqueued at its source router, or dropped
   immediately if unreachable.
3. **TCP timeout check** — any TCP packet that's been awaiting an ACK too
   long gets retransmitted.
4. **Forwarding** — every router with pending packets gets a per-tick
   transmit budget (derived from its links' configured bandwidth); the QoS
   scheduler picks which queued packets go out this tick in strict
   priority order (Voice > Video > Web > Email > File Transfer); each
   selected packet advances one hop, decrementing TTL, and is either
   delivered (triggering a TCP ACK if applicable), dropped (buffer
   overflow, link down, or TTL expiry — triggering a TCP retransmit if
   applicable), or re-queued if its outgoing link is already saturated
   this tick.
5. **Metrics** — queue occupancy and a full `PerformanceSnapshot`
   (throughput, delay, PDR, loss rate, utilization) are recorded.

## Known limitations / good next steps

- The AI optimizer's tabular Q-table doesn't scale to very large
  topologies (state space grows as routers × destinations) — fine for
  classroom-sized networks, would need function approximation (e.g. a
  small neural net) for anything bigger.
- QoS here is strict-priority only (no token buckets or weighted fair
  queueing), matching the priority list in the design doc.
- BGP, MPLS, IPv6, SDN/NFV, and the other "Future Scope" items from the
  design doc are intentionally out of scope for this build.
