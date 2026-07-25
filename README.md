<div align="center">

<img src="docs/assets/logo-wordmark.png" alt="NetSimX" width="480"/>

A Java + JavaFX network simulator — routers, links, packet-by-packet
forwarding, congestion, QoS, TCP/UDP behavior, failures, live charts,
and an AI route optimizer, all running in an actual interactive
dashboard instead of just printing numbers to a console.

![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-21-4fc3f7?logo=java&logoColor=white)
![Tests](https://img.shields.io/badge/tests-38%20passing-43cf94)

<img src="docs/assets/demo.gif" alt="NetSimX live demo" width="800"/>

*Traffic flowing, a link failing mid-run, and the engine rerouting around it. Recorded straight from the running app.*

</div>

---

## Why this exists

Most routing simulators are either a static diagram with arrows, or a
black box that spits out one final number. This one actually shows
packets moving hop by hop — what Dijkstra vs. ECMP looks like when a
link dies mid-transfer, what happens to a voice packet vs. a file
download when a router's queue fills up, whether a Q-learning agent can
actually learn to route around congestion without being told any rules
at all.

## Quick start

```bash
mvn javafx:run      # fetches JavaFX from Maven Central on first run
mvn test             # 38 tests
mvn package && java -jar target/netsimx.jar
```

Needs JDK 17+. Everything besides JavaFX and JUnit is dependency-free —
no SQLite, no PDF library, no JSON library, all hand-rolled.

## Screenshots

<table>
<tr>
<td width="33%"><img src="docs/assets/screenshot-dashboard.png" alt="Dashboard running"/><br/><sub>Running — traffic flowing, charts live</sub></td>
<td width="33%"><img src="docs/assets/screenshot-failure-reroute.png" alt="Link failure and reroute"/><br/><sub>Link down (red), auto-reroute</sub></td>
<td width="33%"><img src="docs/assets/screenshot-inspector.png" alt="Inspector panel"/><br/><sub>Click a router to inspect it live</sub></td>
</tr>
<tr>
<td width="33%"><img src="docs/assets/screenshot-wizard.png" alt="New Simulation Wizard"/><br/><sub>New Simulation Wizard</sub></td>
<td width="33%"><img src="docs/assets/screenshot-benchmark.png" alt="Benchmark Mode results"/><br/><sub>Benchmark Mode — algorithm comparison</sub></td>
<td width="33%"><img src="docs/assets/screenshot-report.png" alt="Report screen"/><br/><sub>Report screen — CSV/PDF export</sub></td>
</tr>
</table>

## Architecture



```
        New Simulation Wizard
                |
                ▼
          NetworkTopology
        (routers + links)
                |
                ▼
        Traffic Generator
   (Voice / Video / Web / Email / File)
                |
                ▼
          Routing Engine
 (Dijkstra / Bellman-Ford / ECMP / AI)
                |
        ┌───────┴───────┐
        ▼               ▼
   TCP Handling     UDP Handling
  (ACK + retry)   (fire-and-forget)
        |               |
        └───────┬───────┘
                ▼
          QoS Scheduler
  (Voice > Video > Web > Email > File)
                |
                ▼
          Router Queues
 (deliver / drop / re-queue, per-link
        bandwidth budget)
                |
                ▼
      Statistics Collector
                |
                ▼
        JavaFX Dashboard
 (live charts, canvas, inspectors)
```

## What's actually in here

| Module | Where to look |
|---|---|
| Topology builder (drag routers, right-click menus) | `model.NetworkTopology`, `gui.TopologyCanvas` |
| Packet engine + live packet inspector | `model.Packet`, `simulation.SimulationEngine`, `gui.PacketInspectorPanel` |
| Routing: Dijkstra, Bellman-Ford, ECMP | `routing/` |
| Congestion, bounded queues, manual congest-a-link | `simulation.QueueManager`, `Link.congest()` |
| Traffic profiles (Voice/Video/Web/Email/File) | `simulation.TrafficGenerator` |
| QoS priority scheduling | `simulation.QoSScheduler` |
| Failures + chaos mode | `simulation.FailureSimulator` |
| TCP retry/ACK vs. fire-and-forget UDP | `simulation.TcpUdpManager` |
| ECMP load balancing (actually per-packet, not cosmetic) | `routing.ECMPRouting` |
| Q-learning route optimizer | `ai.QLearningRouteOptimizer` |
| Live charts + routing table view | `analytics/`, `gui.RoutingTablePanel` |
| JSON topology import/export | `persistence.TopologyIO` |
| Benchmark mode (compare algorithms head to head) | `simulation.BenchmarkRunner` |
| Report screen, CSV/PDF export | `gui.ReportScreen`, `persistence.MiniPdf` |

## A couple of things worth knowing

**The AI optimizer is plain Java, not a Python ML library.** Wiring this
up to something like Stable-Baselines3 would mean a separate Python
process talking to a JavaFX desktop app — a lot of moving parts for what
this needed. So `QLearningRouteOptimizer` is a real, working tabular
Q-learning agent written by hand, no framework, that learns from actual
congestion and adapts its routing over time. It plugs into the same
interface as Dijkstra/Bellman-Ford/ECMP, so swapping in a real external
RL service later would just mean replacing that one class.

**No database.** Topologies save as JSON, stats export as CSV, both
written with small hand-rolled code instead of a library — a network
layout and some time-series numbers don't really need SQLite.

## Right-click things

Right-click a router: inspect it, see its routing table, disable it,
generate traffic from it, rename it, delete it.

Right-click a link: edit bandwidth/latency/packet-loss%, disable it,
manually congest it (drops it to 10% bandwidth so you can watch queues
build up on demand), delete it.

## Getting around the app

Splash screen → **New Simulation** (pick a topology template, an
algorithm, and a traffic preset) or **Open Project** or **Benchmark
Mode**. From inside a running simulation there's a Home button back to a
dashboard with recent projects and a small library of sample topologies
(Campus LAN, Enterprise, Data Center, ISP Backbone, Smart City IoT).

## How a tick actually works

Every simulated tick:

1. Chaos mode maybe kills a link
2. Traffic flows probabilistically spawn new packets, routed and queued
3. Any TCP packet that's been waiting too long for an ACK gets resent
4. Every router forwards what it can this tick, QoS picks priority order
   first (Voice > Video > Web > Email > File Transfer)
5. Stats get recorded — this is what feeds the live charts

That's the whole simulation. Everything in the dashboard is just a
window into this loop running repeatedly.

<img src="docs/assets/sequence-diagram-tick.png" alt="Sequence diagram of one simulation tick" width="800"/>

## Benchmarks

Benchmark Mode runs each algorithm N independent times over the same
topology and traffic, headlessly, then averages the results. Each run
gets a fresh engine — matters most for the AI optimizer, since it starts
cold every time instead of quietly learning across runs.

Example run — ISP Backbone topology (9 routers), 20 runs × 150 ticks per algorithm:

| Algorithm | Avg Delay | Avg Loss | Delivered | Dropped |
|---|---|---|---|---|
| Dijkstra (OSPF) | 65.94 ms | 0.00% | 5,271 | 0 |
| Bellman-Ford (RIP) | 65.75 ms | 0.00% | 5,442 | 0 |
| ECMP | 72.96 ms | 0.00% | 5,371 | 0 |
| AI Route Optimizer | 170.18 ms | 41.17% | 3,867 | — |

**Winner (lowest avg delay): Bellman-Ford.** The AI optimizer's numbers
look worse here on purpose, not by accident — each run resets its
Q-table for a fair comparison, so with only 150 ticks it doesn't get
enough time to actually learn the topology before the run ends. Give it
a longer run or leave "train continuously" on in the interactive
dashboard and it improves noticeably. This table is one real example
run, not a fixed benchmark — numbers will vary with topology, traffic
pattern, and run length, which is the point of having the mode at all.

## Roadmap

Things that would be worth building next, roughly in order of how
contained they are:

- **Real packet corruption** — `Link` already has an independent
  `lossProbability`; adding a `corrupted` flag to `Packet` and wiring it
  through `computeChecksum()` would make the checksum field in the
  Packet Inspector mean something instead of always passing.
- **Function approximation for the AI optimizer** — the Q-table is a
  flat lookup keyed on `(router, destination)`, which won't scale past
  a few dozen routers. A small neural net in place of the table is the
  natural next step.
- **Real TCP congestion control** — right now TCP is fixed-count
  retry-with-timeout, not slow start / AIMD window growth. Would make
  the TCP-vs-UDP demo a lot more convincing under real load.
- **Weighted fair queueing** — QoS is strict-priority only today, so a
  starved low-priority flow just... stays starved. WFQ or a token
  bucket would fix that.
- **Dirty-region canvas rendering** — the topology canvas redraws
  everything from scratch every frame. Fine up to ~15 routers, would
  need real optimization for anything bigger.
- **BGP / MPLS / IPv6 / SDN** — explicitly out of scope for this
  version, but the routing-algorithm interface was built with exactly
  this kind of extension in mind.

## Test coverage, honestly

38 tests, but not evenly spread — worth knowing which parts are
actually covered:

| Package | Covered | Not covered |
|---|---|---|
| `model` | Link mechanics (loss probability, congest/release) | — |
| `routing` | Dijkstra, Bellman-Ford, ECMP all cross-checked against each other | — |
| `simulation` | Engine core, TCP retry/timeout (both real bugs live here), benchmark runner | — |
| `topology` | Every generator template | — |
| `persistence` | PDF generation, recent-projects list | JSON round-trip is only checked by the manual smoke script, not a JUnit test |
| `ai` | — | Zero direct tests. Exercised manually through the dashboard, not automated |
| `analytics` | Indirectly, through engine tests | No dedicated test file |
| `gui` | — | Verified manually during development (screenshots + OCR checks), no automated UI tests |

No coverage tool wired in (no JaCoCo in the build), so this table is
from actually reading which test files exist and what they call — not
a generated percentage.

## Two real bugs, found the hard way

Building the benchmark runner (which hammers TCP way harder than
clicking around the dashboard ever does) turned up two actual bugs in
`TcpUdpManager`, both now covered by regression tests:

- A `ConcurrentModificationException` crash — it modified its own
  tracking list while looping over it, which only broke when two
  packets happened to time out in the same tick.
- The retry limit silently never worked — every retransmitted packet's
  attempt counter got reset back to 1 by an unrelated, normal code path,
  so a stuck TCP flow could in theory retry forever. One-word fix
  (`putIfAbsent` instead of `put`), but it took writing a test that
  mimicked the real call sequence to actually find it.

## Known limitations

- The AI's Q-table is a plain lookup table — fine for small networks,
  won't scale to hundreds of routers.
- Canvas redraws everything from scratch every frame — untested past
  ~15 routers.
- TCP here is retry-with-timeout, not real congestion control.
- QoS is strict priority only, no weighted fair queueing.
- ECMP caps at 8 equal-cost paths per destination.
- No BGP/MPLS/IPv6/SDN — out of scope for this.

## Project layout

```
netsimx/
├── pom.xml
├── config/               # sample topologies + a small library of them
├── docs/                 # screenshots, architecture diagram, full write-up
└── src/
    ├── main/java/com/netsimx/
    │   ├── model/          # Router, Link, Packet, NetworkTopology
    │   ├── routing/        # Dijkstra, Bellman-Ford, ECMP
    │   ├── simulation/     # the engine, traffic, QoS, failures, TCP/UDP, benchmark runner
    │   ├── ai/              # Q-learning optimizer
    │   ├── analytics/       # stats collector
    │   ├── topology/        # topology generators
    │   ├── persistence/     # JSON/CSV/PDF, no external libs
    │   └── gui/              # everything you see and click
    └── test/java/com/netsimx/   # 38 JUnit tests
```

## Docs

Longer write-up in [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md),
and a full 32-page PDF version in [docs/book/](docs/book/) if you want
every file explained in depth.
