# Changelog

All notable changes to this project, grouped by the actual development
phases they happened in. See `git log` for the full, unedited commit
history this is built from.

## [0.1.0] — Core engine

- Initial Maven/JavaFX project scaffold
- Network model: `Router`, `Link`, `Packet`, `NetworkTopology`
- Routing engine: Dijkstra (OSPF), Bellman-Ford (RIP), ECMP
- Simulation engine: traffic generation, QoS scheduling, congestion,
  failure injection, TCP/UDP behavior
- AI route optimizer (from-scratch Q-learning)
- Performance analytics collector
- JSON topology import/export, CSV stats export
- Interactive JavaFX dashboard (first version)
- Sample 9-router campus network topology
- JUnit test suite + manual smoke check

## [0.2.0] — Fixes and presentation

- Fixed: window could open off-screen depending on monitor setup
- Fixed: packaged jar failed to launch (`JavaFX runtime components are
  missing`) — added a `Launcher` indirection class
- Lowered minimum Java version from 21 to 17 (no 21-only features were
  actually in use)
- Scripted demo mode for deterministic screenshot/GIF capture
- Project logo, architecture diagram, gallery screenshots, demo GIF
- GitHub Actions CI (build + test on JDK 17 and 21)
- README rewrite

## [0.3.0] — Interactive inspectors

- Network / Packet Inspector / Routing Table tabs
- Right-click context menus on routers and links
- New `Link` mechanics: packet-loss probability, congest/release
- Congestion-event and failure-event counters
- Tests for the new link mechanics

## [0.4.0] — Fixed TCP bugs found while stress-testing

- Fixed: `ConcurrentModificationException` crash in
  `TcpUdpManager.checkTimeouts()` when multiple packets timed out in
  the same tick
- Fixed: max-retransmission ceiling was silently never enforced (an
  unrelated re-admission code path reset the attempt counter every
  time)
- Regression tests for both

## [0.5.0] — App navigation, Benchmark Mode, Reports

- Splash Screen → New Simulation Wizard → Workspace flow
- Home Dashboard with persisted Recent Projects
- Procedural topology generators (Star/Mesh/Ring/Tree/ISP Backbone) and
  a sample topology library
- Headless multi-run `BenchmarkRunner` for algorithm comparison
- Report screen with CSV and hand-rolled PDF export

## [1.0.0] — Documentation

- Full project documentation (Markdown + book-style PDF)
- Known limitations documented, matching `TODO`/`FIXME` markers left in
  the code at the exact spots they apply to
