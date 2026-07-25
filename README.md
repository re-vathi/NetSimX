<div align="center">

<img src="docs/assets/logo-wordmark.png" alt="NetSimX" width="480"/>

A Java + JavaFX network simulator — routers, links, packet-by-packet
forwarding, congestion, QoS, TCP/UDP behavior, failures, live charts,
and an AI route optimizer, all running in an interactive dashboard.

</div>

---

## Screenshot

![NetSimX dashboard](docs/assets/screenshot-dashboard.png)

## Quick start

```bash
mvn javafx:run
```

Needs JDK 17+. First run downloads JavaFX + JUnit from Maven Central,
nothing else is required.

```bash
mvn test                              # 38 tests
mvn package && java -jar target/netsimx.jar   # standalone jar
```

## What's in it

- Routing: Dijkstra, Bellman-Ford, ECMP, and a from-scratch Q-learning
  route optimizer
- Congestion, bounded queues, QoS priority scheduling
- TCP (ACK + retransmit) vs. UDP (fire-and-forget), modeled at the
  packet level
- Manual + automatic (chaos mode) link/router failures with live
  reroute
- Click any router or in-flight packet for live inspector panels
- Benchmark mode — compare algorithms head to head
- Save/load topologies as JSON, export stats as CSV/PDF

## Docs

Longer write-up in [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md),
full details in [docs/book/](docs/book/).
