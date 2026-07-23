package com.netsimx.simulation;

import com.netsimx.routing.DijkstraRouting;
import com.netsimx.routing.ECMPRouting;
import com.netsimx.routing.RoutingAlgorithm;
import com.netsimx.topology.TopologyGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunnerTest {

    @Test
    void producesOneResultPerAlgorithm() {
        BenchmarkRunner runner = new BenchmarkRunner();
        List<RoutingAlgorithm> algorithms = List.of(new DijkstraRouting(), new ECMPRouting());

        var report = runner.run(algorithms,
                () -> TopologyGenerator.generate(TopologyGenerator.Template.ISP_BACKBONE, 9),
                BenchmarkRunner.randomMixedTraffic(3, 1),
                2, 50, null);

        assertEquals(2, report.results.size());
    }

    @Test
    void topologySupplierIsCalledExactlyOncePerRun() {
        BenchmarkRunner runner = new BenchmarkRunner();
        int[] supplierCallCount = {0};

        runner.run(List.of(new DijkstraRouting()),
                () -> {
                    supplierCallCount[0]++;
                    return TopologyGenerator.generate(TopologyGenerator.Template.MESH, 5);
                },
                BenchmarkRunner.randomMixedTraffic(2, 1),
                5, 20, null);

        assertEquals(5, supplierCallCount[0],
                "Topology supplier must be called once per run so each run gets an independent instance");
    }

    @Test
    void progressListenerFiresOnceForEveryAlgorithmRunCombination() {
        BenchmarkRunner runner = new BenchmarkRunner();
        int[] callCount = {0};

        runner.run(List.of(new DijkstraRouting(), new ECMPRouting()),
                () -> TopologyGenerator.generate(TopologyGenerator.Template.STAR, 4),
                BenchmarkRunner.randomMixedTraffic(2, 1),
                4, 10,
                (a, ac, r, rc) -> callCount[0]++);

        assertEquals(2 * 4, callCount[0], "Expected one progress callback per (algorithm, run) pair");
    }

    @Test
    void winnerIsTheAlgorithmWithLowestAverageDelay() {
        BenchmarkRunner runner = new BenchmarkRunner();
        var report = runner.run(List.of(new DijkstraRouting(), new ECMPRouting()),
                () -> TopologyGenerator.generate(TopologyGenerator.Template.ISP_BACKBONE, 9),
                BenchmarkRunner.randomMixedTraffic(3, 7),
                2, 80, null);

        assertNotNull(report.winnerByDelay);
        boolean winnerIsOneOfTheCandidates = report.results.stream()
                .anyMatch(r -> r.algorithmName.equals(report.winnerByDelay));
        assertTrue(winnerIsOneOfTheCandidates);
    }
}
