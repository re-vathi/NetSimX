package com.netsimx.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A single node in the simulated network. Routers hold a bounded packet
 * queue (used by {@link com.netsimx.simulation.QueueManager} /
 * {@link com.netsimx.simulation.QoSScheduler} to model congestion) and a
 * position used purely by the GUI for topology rendering.
 */
public class Router {

    public enum Status { UP, DOWN }

    private final String id;
    private String label;
    private double x;
    private double y;
    private Status status = Status.UP;

    /** Bounded FIFO used as the "physical" transmit queue for this router. */
    private final int queueCapacity;
    private final Deque<Packet> queue = new ArrayDeque<>();

    private long packetsForwarded = 0;
    private long packetsDropped = 0;

    public Router(String id, String label, double x, double y, int queueCapacity) {
        this.id = id;
        this.label = label;
        this.x = x;
        this.y = y;
        this.queueCapacity = queueCapacity;
    }

    public Router(String id, double x, double y) {
        this(id, id, x, y, 64);
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isUp() { return status == Status.UP; }

    public int getQueueCapacity() { return queueCapacity; }
    public int getQueueSize() { return queue.size(); }
    public double getQueueOccupancy() { return queueCapacity == 0 ? 0 : (double) queue.size() / queueCapacity; }

    public long getPacketsForwarded() { return packetsForwarded; }
    public long getPacketsDropped() { return packetsDropped; }

    /**
     * Attempt to enqueue a packet for transmission. Returns false (and
     * records a drop) if the queue is full - this is how buffer overflow /
     * congestion loss is modeled.
     */
    public boolean enqueue(Packet packet) {
        if (queue.size() >= queueCapacity) {
            packetsDropped++;
            return false;
        }
        queue.addLast(packet);
        return true;
    }

    /** Enqueue at the front - used by QoS scheduler to jump high-priority traffic ahead. */
    public boolean enqueuePriority(Packet packet) {
        if (queue.size() >= queueCapacity) {
            packetsDropped++;
            return false;
        }
        queue.addFirst(packet);
        return true;
    }

    public Packet peek() {
        return queue.peekFirst();
    }

    public Packet dequeue() {
        Packet p = queue.pollFirst();
        if (p != null) packetsForwarded++;
        return p;
    }

    public List<Packet> drainAll() {
        List<Packet> drained = new ArrayList<>(queue);
        queue.clear();
        return drained;
    }

    public boolean hasPending() {
        return !queue.isEmpty();
    }

    public void resetStats() {
        packetsForwarded = 0;
        packetsDropped = 0;
    }

    @Override
    public String toString() {
        return "Router{" + id + ", q=" + queue.size() + "/" + queueCapacity + ", " + status + "}";
    }
}
