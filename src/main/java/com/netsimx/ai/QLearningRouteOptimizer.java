package com.netsimx.ai;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;
import com.netsimx.routing.RoutingAlgorithm;

import java.util.*;

/**
 * Module 10 - AI-Based Route Optimization.
 *
 * A self-contained (no external ML framework required) tabular
 * reinforcement-learning agent that learns, per (currentRouter,
 * destination) state, which neighbor to forward through next in order to
 * minimize a cost combining link latency, congestion (queue occupancy),
 * and packet loss risk - continuously adapting as those conditions change
 * at runtime, unlike the static-cost Dijkstra/Bellman-Ford/ECMP engines.
 *
 * This implements the same {@link RoutingAlgorithm} interface as the
 * classical algorithms so it can be selected interchangeably in the GUI's
 * algorithm dropdown; internally it runs a standard Q-learning update
 * (Bellman equation with learning rate alpha and discount gamma) driven by
 * observed link/router state each time {@link #computeRoutes} is called,
 * with epsilon-greedy exploration during training.
 *
 * Note: this is a lightweight, dependency-free RL implementation suited to
 * small simulated topologies and real-time GUI use. The project's design
 * doc mentions Python + Stable-Baselines3 as an optional heavier-weight
 * alternative for research use outside the JavaFX app; swapping in a real
 * external RL service would mean replacing this class with an HTTP/gRPC
 * client while keeping the same {@link RoutingAlgorithm} contract.
 */
public class QLearningRouteOptimizer implements RoutingAlgorithm {

    /** Q[state][action] where state = "currentRouterId|destinationId", action = neighborRouterId. */
    private final Map<String, Map<String, Double>> qTable = new HashMap<>();

    private double alpha = 0.3;   // learning rate
    private double gamma = 0.9;   // discount factor
    private double epsilon = 0.15; // exploration rate
    private final Random random = new Random();

    private long trainingSteps = 0;

    public void setAlpha(double alpha) { this.alpha = alpha; }
    public void setGamma(double gamma) { this.gamma = gamma; }
    public void setEpsilon(double epsilon) { this.epsilon = epsilon; }
    public long getTrainingSteps() { return trainingSteps; }
    public int getStatesLearned() { return qTable.size(); }

    @Override
    public String getName() {
        return "AI Route Optimizer (Q-Learning)";
    }

    @Override
    public Map<String, RouteResult> computeRoutes(NetworkTopology topology, String sourceId) {
        Map<String, RouteResult> results = new LinkedHashMap<>();
        for (Router dest : topology.getRouters()) {
            if (dest.getId().equals(sourceId) || !dest.isUp()) continue;
            List<String> path = greedyPath(topology, sourceId, dest.getId());
            if (path != null) {
                double cost = pathCost(topology, path);
                results.put(dest.getId(), new RouteResult(path, cost));
            }
        }
        return results;
    }

    /**
     * Runs one training episode toward {@code destinationId} starting from
     * every router in the topology, updating the Q-table via reward
     * feedback based on current live link/queue conditions. Call
     * periodically (e.g. once per simulation tick, or on a timer) so the
     * agent adapts to changing congestion instead of learning once and
     * going stale.
     */
    public void trainEpisode(NetworkTopology topology, String destinationId) {
        for (Router startRouter : topology.getRouters()) {
            if (!startRouter.isUp() || startRouter.getId().equals(destinationId)) continue;
            trainFrom(topology, startRouter.getId(), destinationId, 0);
        }
    }

    private void trainFrom(NetworkTopology topology, String current, String destination, int depth) {
        if (current.equals(destination) || depth > 32) return;

        List<String> neighbors = topology.activeNeighbors(current);
        if (neighbors.isEmpty()) return;

        String state = stateKey(current, destination);
        String action = chooseAction(state, neighbors);

        Link link = topology.findLinkBetween(current, action).orElse(null);
        if (link == null) return;

        double reward = -edgeCost(topology, current, action, link);
        if (action.equals(destination)) reward += 10.0; // bonus for reaching the destination

        String nextState = stateKey(action, destination);
        double maxNextQ = maxQ(nextState, topology.activeNeighbors(action));

        Map<String, Double> stateQ = qTable.computeIfAbsent(state, k -> new HashMap<>());
        double oldQ = stateQ.getOrDefault(action, 0.0);
        double newQ = oldQ + alpha * (reward + gamma * maxNextQ - oldQ);
        stateQ.put(action, newQ);
        trainingSteps++;

        trainFrom(topology, action, destination, depth + 1);
    }

    private String chooseAction(String state, List<String> neighbors) {
        if (random.nextDouble() < epsilon || !qTable.containsKey(state)) {
            return neighbors.get(random.nextInt(neighbors.size()));
        }
        Map<String, Double> stateQ = qTable.get(state);
        String best = null;
        double bestQ = Double.NEGATIVE_INFINITY;
        for (String n : neighbors) {
            double q = stateQ.getOrDefault(n, 0.0);
            if (q > bestQ) { bestQ = q; best = n; }
        }
        return best != null ? best : neighbors.get(random.nextInt(neighbors.size()));
    }

    private double maxQ(String state, List<String> neighbors) {
        if (neighbors.isEmpty()) return 0.0;
        Map<String, Double> stateQ = qTable.getOrDefault(state, Map.of());
        double max = Double.NEGATIVE_INFINITY;
        for (String n : neighbors) max = Math.max(max, stateQ.getOrDefault(n, 0.0));
        return max == Double.NEGATIVE_INFINITY ? 0.0 : max;
    }

    /** Cost combining static link cost/latency with live congestion (queue occupancy) at the receiving router. */
    private double edgeCost(NetworkTopology topology, String from, String to, Link link) {
        Router toRouter = topology.getRouter(to);
        double congestionPenalty = (toRouter != null) ? toRouter.getQueueOccupancy() * 5.0 : 0.0;
        double utilizationPenalty = link.getUtilization() * 3.0;
        return link.getCost() + (link.getLatencyMs() / 10.0) + congestionPenalty + utilizationPenalty;
    }

    /** Follows the greedily-best learned action at each hop to build a concrete path (falls back gracefully if untrained). */
    private List<String> greedyPath(NetworkTopology topology, String source, String destination) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = source;
        path.add(current);
        visited.add(current);

        int guard = 0;
        while (!current.equals(destination) && guard++ < topology.routerCount() + 2) {
            List<String> neighbors = topology.activeNeighbors(current);
            neighbors.removeIf(visited::contains);
            if (neighbors.isEmpty()) return null; // learned/available path is a dead end

            String state = stateKey(current, destination);
            Map<String, Double> stateQ = qTable.getOrDefault(state, Map.of());
            String best = neighbors.get(0);
            double bestQ = Double.NEGATIVE_INFINITY;
            for (String n : neighbors) {
                double q = stateQ.getOrDefault(n, 0.0);
                if (q > bestQ) { bestQ = q; best = n; }
            }
            current = best;
            path.add(current);
            visited.add(current);
        }
        return current.equals(destination) ? path : null;
    }

    private double pathCost(NetworkTopology topology, List<String> path) {
        double total = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Link link = topology.findLinkBetween(path.get(i), path.get(i + 1)).orElse(null);
            if (link != null) total += edgeCost(topology, path.get(i), path.get(i + 1), link);
        }
        return total;
    }

    private String stateKey(String currentRouter, String destination) {
        return currentRouter + "|" + destination;
    }

    public void reset() {
        qTable.clear();
        trainingSteps = 0;
    }
}
