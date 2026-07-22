package com.netsimx.simulation;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Router;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Injects and clears network failures (Module 7). Failures are applied by
 * simply flipping {@link Link#setUp} / {@link Router#setStatus} - the
 * routing engine is expected to notice via its normal
 * "topology changed -&gt; recompute" hook, exactly mirroring how real
 * link-state/distance-vector protocols reconverge after an outage, no
 * special-casing required elsewhere in the simulator.
 */
public class FailureSimulator {

    private final Random random = new Random();
    /** If >0, randomly fail a link/router with this probability per tick (chaos-testing mode). */
    private double randomFailureProbabilityPerTick = 0.0;

    public void setRandomFailureProbabilityPerTick(double p) {
        this.randomFailureProbabilityPerTick = Math.max(0, Math.min(1, p));
    }

    public double getRandomFailureProbabilityPerTick() {
        return randomFailureProbabilityPerTick;
    }

    public void failLink(Link link, BiConsumer<Link, Boolean> onChange) {
        if (link.isUp()) {
            link.setUp(false);
            if (onChange != null) onChange.accept(link, false);
        }
    }

    public void recoverLink(Link link, BiConsumer<Link, Boolean> onChange) {
        if (!link.isUp()) {
            link.setUp(true);
            if (onChange != null) onChange.accept(link, true);
        }
    }

    public void failRouter(Router router, BiConsumer<Router, Boolean> onChange) {
        if (router.isUp()) {
            router.setStatus(Router.Status.DOWN);
            if (onChange != null) onChange.accept(router, false);
        }
    }

    public void recoverRouter(Router router, BiConsumer<Router, Boolean> onChange) {
        if (!router.isUp()) {
            router.setStatus(Router.Status.UP);
            if (onChange != null) onChange.accept(router, true);
        }
    }

    /**
     * Chaos-testing hook: called once per tick by the engine; with
     * probability {@link #randomFailureProbabilityPerTick}, randomly takes
     * down one link. Returns the link that was failed, or null if none.
     */
    public Link maybeInjectRandomFailure(NetworkTopology topology, BiConsumer<Link, Boolean> onChange) {
        if (randomFailureProbabilityPerTick <= 0) return null;
        if (random.nextDouble() >= randomFailureProbabilityPerTick) return null;

        var upLinks = topology.getLinks().stream().filter(Link::isUp).toList();
        if (upLinks.isEmpty()) return null;

        Link victim = upLinks.get(random.nextInt(upLinks.size()));
        failLink(victim, onChange);
        return victim;
    }
}
