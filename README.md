<div align="center">

<img src="docs/assets/logo-wordmark.png" alt="NetSimX" width="560"/>

A Java + JavaFX network simulator — routers, links, packet-by-packet
forwarding, congestion, QoS, TCP/UDP behavior, failures, live charts, and
an AI route optimizer, all running in an actual interactive dashboard
instead of just printing numbers to a console.

<!-- Replace OWNER/REPO below once this is pushed to your own GitHub repo -->
[![CI](https://img.shields.io/github/actions/workflow/status/OWNER/REPO/ci.yml?branch=main&label=build&logo=github)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-21-4fc3f7?logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Tests](https://img.shields.io/badge/tests-38%20passing-43cf94)
![License](https://img.shields.io/badge/license-MIT-blue)

<img src="docs/assets/demo.gif" alt="NetSimX live demo" width="820"/>

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
no SQLite, no PDF library, no JSON library, all hand-rolled. Partly
because it kept the build simple, partly because it was more fun to
write a tiny PDF generator from scratch than to add another dependency.

## Screenshots

<table>
<tr>
<td width="25%"><img src="docs/screenshot.png" alt="Fresh topology load"/><br/><sub>Fresh load, paused</sub></td>
<td width="25%"><img src="docs/assets/screenshot-dashboard.png" alt="Simulation running"/><br/><sub>Running, charts live</sub></td>
<td width="25%"><img src="docs/assets/screenshot-failure-reroute.png" alt="Link failure and reroute"/><br/><sub>Link down, auto-reroute</sub></td>
<td width="25%"><img src="docs/assets/screenshot-inspector.png" alt="Inspector panel"/><br/><sub>Click a router to inspect it</sub></td>
</tr>
</table>

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
written with small hand-rolled code instead of a library. It's honestly
overkill for what this needs to persist — a network layout and some
time-series numbers don't need a database.

## Right-click things

Right-click a router: inspect it, see its routing table, disable it,
generate traffic from it, rename it, delete it.

Right-click a link: edit bandwidth/latency/packet-loss%, disable it,
manually congest it (drops it to 10% bandwidth so you can watch queues
build up on demand instead of waiting for real traffic to do it),
delete it.

## Getting around the app

Splash screen → **New Simulation** (pick a topology template, an
algorithm, and a traffic preset) or **Open Project** or **Benchmark
Mode**. From inside a running simulation there's a Home button back to a
dashboard with recent projects and a small library of sample topologies
(Campus LAN, Enterprise, Data Center, ISP Backbone, Smart City IoT).

**Benchmark mode** runs each algorithm N times over the same topology
and traffic, headlessly (no GUI, ticks fire back-to-back on a background
thread), then compares them in a table + bar chart. Each run gets a
fresh engine, which matters most for the AI optimizer — it starts cold
every time so it's not quietly cheating by learning across runs.

**Report screen** dumps a summary of the current run — packets, loss
rate, delay, congestion events, failures — with CSV and PDF export
buttons.

## The dashboard controls, briefly

- Start / Pause / Step / Reset, tick speed slider
- Switch routing algorithm live, mid-simulation
- Add a router/link by clicking the canvas, drag to reposition
- Traffic generator panel — add flows by router + type, or randomize
- Chaos mode — random failures on a timer, for unattended stress testing
- Load/Save topology as JSON, export stats as CSV
- Live charts (throughput, delay, PDR, utilization) + an event log

## How a tick actually works

Every simulated tick:

1. Chaos mode maybe kills a link
2. Traffic flows probabilistically spawn new packets, routed and queued
3. Any TCP packet that's been waiting too long for an ACK gets resent
4. Every router forwards what it can this tick, QoS picks priority order
   first (Voice > Video > Web > Email > File Transfer), packets get
   delivered / dropped / requeued accordingly
5. Stats get recorded — this is what feeds the live charts

That's genuinely the whole simulation. Everything visible in the
dashboard is just a window into this loop running repeatedly.

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

Neither showed up in a few minutes of manual testing — both needed
sustained traffic to trigger, which is exactly why the benchmark runner
ended up being useful for more than just benchmarking.

## Known limitations

- The AI's Q-table is a plain lookup table — fine for small networks,
  won't scale to hundreds of routers without switching to function
  approximation.
- Canvas redraws everything from scratch every frame. Untested past
  ~15 routers, could get slow on something much bigger.
- TCP here is retry-with-timeout, not real congestion control — no slow
  start, no window scaling.
- QoS is strict priority only, no weighted fair queueing.
- ECMP caps at 8 equal-cost paths per destination.
- No BGP/MPLS/IPv6/SDN — out of scope for this.

A few of these are also left as `TODO`/`FIXME` comments at the exact
spots in the code they apply to.

## Project layout

```
netsimx/
├── pom.xml
├── LICENSE / CHANGELOG.md / CONTRIBUTING.md
├── .github/workflows/ci.yml
├── config/
│   ├── sample-network.json
│   └── samples/            # Campus LAN, Enterprise, Data Center, ISP, Smart City
├── docs/                   # this file's images, book-length PDF writeup
└── src/
    ├── main/java/com/netsimx/
    │   ├── model/           # Router, Link, Packet, NetworkTopology
    │   ├── routing/         # Dijkstra, Bellman-Ford, ECMP
    │   ├── simulation/      # the engine, traffic, QoS, failures, TCP/UDP, benchmark runner
    │   ├── ai/               # Q-learning optimizer
    │   ├── analytics/        # stats collector
    │   ├── topology/         # topology generators
    │   ├── persistence/      # JSON/CSV/PDF, no external libs
    │   └── gui/              # everything you see and click
    └── test/java/com/netsimx/   # 38 JUnit tests + a standalone smoke check
```

## Docs

There's a longer write-up in [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md),
and an even longer 32-page PDF version in [docs/book/](docs/book/) if
you want the full story — every file explained, the bugs above written
up in more detail, and a guide to extending the project.

## License

MIT — see [LICENSE](LICENSE).
