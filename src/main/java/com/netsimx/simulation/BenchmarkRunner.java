package com.netsimx.simulation;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;
import com.netsimx.routing.RoutingAlgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Module: Benchmark Mode. Runs a topology/traffic scenario headlessly
 * (no GUI, no wall-clock pacing - ticks fire back-to-back as fast as the
 * JVM can go) once per requested run, for each candidate
 * {@link RoutingAlgorithm}, and aggregates the results so different
 * algorithms can be compared on equal footing under identical traffic.
 *
 * Each run gets a **fresh** topology clone (via {@code topologySupplier})
 * and a fresh {@link SimulationEngine}, so runs don't share state/warm
 * queues with each other - this matters for the AI optimizer in
 * particular, whose Q-table would otherwise keep learning across runs and
 * unfairly outperform its first-run numbers.
 */
public class BenchmarkRunner {

    /** Aggregated result for one algorithm across all its runs. */
    public static class AlgorithmResult {
        public final String algorithmName;
        public final int runs;
        public final double avgDelayMs;
        public final double avgLossRate;
        public final double avgThroughputPps;
        public final double avgRouterUtilization;
        public final long totalDelivered;
        public final long totalDropped;

        AlgorithmResult(String algorithmName, int runs, double avgDelayMs, double avgLossRate,
                         double avgThroughputPps, double avgRouterUtilization, long totalDelivered, long totalDropped) {
            this.algorithmName = algorithmName;
            this.runs = runs;
            this.avgDelayMs = avgDelayMs;
            this.avgLossRate = avgLossRate;
            this.avgThroughputPps = avgThroughputPps;
            this.avgRouterUtilization = avgRouterUtilization;
            this.totalDelivered = totalDelivered;
            this.totalDropped = totalDropped;
        }
    }

    public static class BenchmarkReport {
        public final List<AlgorithmResult> results;
        public final String winnerByDelay;

        BenchmarkReport(List<AlgorithmResult> results) {
            this.results = results;
            this.winnerByDelay = results.stream()
                    .filter(r -> r.totalDelivered > 0)
                    .min((a, b) -> Double.compare(a.avgDelayMs, b.avgDelayMs))
                    .map(r -> r.algorithmName)
                    .orElse("N/A");
        }
    }

    /** Progress callback: (algorithmIndex, algorithmCount, runIndex, runCount) -> void, for a GUI progress bar. */
    public interface ProgressListener {
        void onProgress(int algorithmIndex, int algorithmCount, int runIndex, int runCount);
    }

    /**
     * @param algorithms        candidates to compare
     * @param topologySupplier  produces a fresh, independent topology instance per run (e.g. reload from JSON, or a generator call)
     * @param flowConfigurator  configures traffic flows on a freshly-built engine before each run
     * @param runsPerAlgorithm  how many independent runs to average per algorithm
     * @param ticksPerRun       how many simulation ticks each run executes
     * @param progressListener  optional; may be null
     */
    public BenchmarkReport run(List<RoutingAlgorithm> algorithms,
                                Supplier<NetworkTopology> topologySupplier,
                                java.util.function.Consumer<SimulationEngine> flowConfigurator,
                                int runsPerAlgorithm,
                                int ticksPerRun,
                                ProgressListener progressListener) {
        List<AlgorithmResult> results = new ArrayList<>();

        for (int a = 0; a < algorithms.size(); a++) {
            RoutingAlgorithm algorithm = algorithms.get(a);

            double delaySum = 0, lossSum = 0, throughputSum = 0, utilSum = 0;
            long deliveredSum = 0, droppedSum = 0;
            int successfulRuns = 0;

            for (int run = 0; run < runsPerAlgorithm; run++) {
                if (progressListener != null) {
                    progressListener.onProgress(a, algorithms.size(), run, runsPerAlgorithm);
                }

                NetworkTopology topology = topologySupplier.get();
                SimulationEngine engine = new SimulationEngine(topology);
                engine.setRoutingAlgorithm(algorithm);
                flowConfigurator.accept(engine);

                for (int t = 0; t < ticksPerRun; t++) {
                    engine.tick();
                }

                var stats = engine.getStatistics();
                var history = stats.getHistory();
                if (!history.isEmpty()) {
                    var last = history.get(history.size() - 1);
                    delaySum += last.avgEndToEndDelayMs;
                    lossSum += last.packetLossRate;
                    throughputSum += last.throughputPacketsPerSec;
                    utilSum += (last.avgRouterUtilization + last.avgLinkUtilization) / 2.0;
                    successfulRuns++;
                }
                deliveredSum += stats.getTotalDelivered();
                droppedSum += stats.getTotalDropped();
            }

            int n = Math.max(1, successfulRuns);
            results.add(new AlgorithmResult(algorithm.getName(), runsPerAlgorithm,
                    delaySum / n, lossSum / n, throughputSum / n, utilSum / n, deliveredSum, droppedSum));
        }

        return new BenchmarkReport(results);
    }

    /** Convenience: a flow configurator that mirrors a "typical mixed traffic" scenario across every UP router pair. */
    public static java.util.function.Consumer<SimulationEngine> randomMixedTraffic(int flowCount, long randomSeed) {
        return engine -> {
            java.util.Random random = new java.util.Random(randomSeed);
            NetworkTopology topo = engine.getTopology();
            List<Router> up = topo.getRouters().stream().filter(Router::isUp).toList();
            if (up.size() < 2) return;
            TrafficGenerator.TrafficType[] types = TrafficGenerator.TrafficType.values();
            for (int i = 0; i < flowCount; i++) {
                Router src = up.get(random.nextInt(up.size()));
                Router dst;
                do { dst = up.get(random.nextInt(up.size())); } while (dst == src);
                TrafficGenerator.TrafficType type = types[random.nextInt(types.length)];
                engine.getTrafficGenerator().addFlow(new TrafficGenerator.Flow(src.getId(), dst.getId(), type, 0.2));
            }
        };
    }
}
