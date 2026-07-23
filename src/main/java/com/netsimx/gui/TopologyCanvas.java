package com.netsimx.gui;

import com.netsimx.model.Link;
import com.netsimx.model.NetworkTopology;
import com.netsimx.model.Packet;
import com.netsimx.model.Router;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Module 12 - Interactive Dashboard: a Canvas-based renderer for the
 * network topology, with animated in-flight packets drawn as small dots
 * sliding along their current link. Also handles basic mouse interaction
 * (drag to reposition routers, click to select a router/link for the
 * control panel, right-click to toggle up/down for quick failure testing).
 */
public class TopologyCanvas extends Canvas {

    private static final double ROUTER_RADIUS = 22;

    private NetworkTopology topology;
    /** Snapshot of packets currently visible mid-hop, keyed by packet ID, with interpolation progress 0..1. */
    private final Map<Long, PacketAnim> animatedPackets = new LinkedHashMap<>();

    private Router draggingRouter = null;
    private Router selectedRouter = null;
    private Link selectedLink = null;

    private Consumer<Router> onRouterSelected;
    private Consumer<Link> onLinkSelected;
    private Consumer<Packet> onPacketSelected;
    private BiConsumer<Router, MouseEvent> onRouterContextMenu;
    private BiConsumer<Link, MouseEvent> onLinkContextMenu;
    private BiConsumer<Double, Double> onEmptyAreaClicked;

    private static class PacketAnim {
        double progress; // 0..1 across current hop
        String fromId;
        String toId;
        Color color;
        Packet packet; // live reference - keeps the inspector panel up to date while in flight
    }

    public TopologyCanvas(double width, double height) {
        super(width, height);
        widthProperty().addListener((obs, o, n) -> redraw());
        heightProperty().addListener((obs, o, n) -> redraw());
        setupMouseHandlers();
    }

    public void setTopology(NetworkTopology topology) {
        this.topology = topology;
        redraw();
    }

    public void setOnRouterSelected(Consumer<Router> handler) { this.onRouterSelected = handler; }
    public void setOnLinkSelected(Consumer<Link> handler) { this.onLinkSelected = handler; }
    public void setOnPacketSelected(Consumer<Packet> handler) { this.onPacketSelected = handler; }
    public void setOnEmptyAreaClicked(BiConsumer<Double, Double> handler) { this.onEmptyAreaClicked = handler; }

    public Router getSelectedRouter() { return selectedRouter; }
    public Link getSelectedLink() { return selectedLink; }
    public void clearSelection() { selectedRouter = null; selectedLink = null; }
    public void setOnRouterContextMenu(BiConsumer<Router, MouseEvent> handler) { this.onRouterContextMenu = handler; }
    public void setOnLinkContextMenu(BiConsumer<Link, MouseEvent> handler) { this.onLinkContextMenu = handler; }

    // ------------------------------------------------------------------ //
    // Mouse interaction
    // ------------------------------------------------------------------ //

    private void setupMouseHandlers() {
        setOnMousePressed(e -> {
            if (topology == null) return;

            if (e.getButton() == MouseButton.PRIMARY) {
                Packet packetHit = packetAt(e.getX(), e.getY());
                if (packetHit != null) {
                    if (onPacketSelected != null) onPacketSelected.accept(packetHit);
                    return;
                }

                Router hit = routerAt(e.getX(), e.getY());
                if (hit != null) {
                    draggingRouter = hit;
                    selectedRouter = hit;
                    selectedLink = null;
                    if (onRouterSelected != null) onRouterSelected.accept(hit);
                } else {
                    Link linkHit = linkAt(e.getX(), e.getY());
                    selectedLink = linkHit;
                    selectedRouter = null;
                    if (linkHit != null && onLinkSelected != null) {
                        onLinkSelected.accept(linkHit);
                    } else if (linkHit == null && onEmptyAreaClicked != null) {
                        onEmptyAreaClicked.accept(e.getX(), e.getY());
                    }
                }
                redraw();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                Router hit = routerAt(e.getX(), e.getY());
                if (hit != null) {
                    if (onRouterContextMenu != null) onRouterContextMenu.accept(hit, e);
                } else {
                    Link linkHit = linkAt(e.getX(), e.getY());
                    if (linkHit != null && onLinkContextMenu != null) onLinkContextMenu.accept(linkHit, e);
                }
            }
        });
        setOnMouseDragged(e -> {
            if (draggingRouter != null) {
                draggingRouter.setX(clamp(e.getX(), ROUTER_RADIUS, getWidth() - ROUTER_RADIUS));
                draggingRouter.setY(clamp(e.getY(), ROUTER_RADIUS, getHeight() - ROUTER_RADIUS));
                redraw();
            }
        });
        setOnMouseReleased(e -> draggingRouter = null);
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Hit-test the animated packet dots (checked before routers/links since they're the smallest, most specific target). */
    private Packet packetAt(double x, double y) {
        if (topology == null) return null;
        for (PacketAnim anim : animatedPackets.values()) {
            Router a = topology.getRouter(anim.fromId);
            Router b = topology.getRouter(anim.toId);
            if (a == null || b == null) continue;
            double px = a.getX() + (b.getX() - a.getX()) * anim.progress;
            double py = a.getY() + (b.getY() - a.getY()) * anim.progress;
            double dx = px - x, dy = py - y;
            if (Math.sqrt(dx * dx + dy * dy) <= 9) return anim.packet;
        }
        return null;
    }

    private Router routerAt(double x, double y) {
        if (topology == null) return null;
        for (Router r : topology.getRouters()) {
            double dx = r.getX() - x, dy = r.getY() - y;
            if (Math.sqrt(dx * dx + dy * dy) <= ROUTER_RADIUS) return r;
        }
        return null;
    }

    private Link linkAt(double x, double y) {
        if (topology == null) return null;
        for (Link l : topology.getLinks()) {
            Router a = topology.getRouter(l.getRouterAId());
            Router b = topology.getRouter(l.getRouterBId());
            if (a == null || b == null) continue;
            double dist = pointToSegmentDistance(x, y, a.getX(), a.getY(), b.getX(), b.getY());
            if (dist < 8) return l;
        }
        return null;
    }

    private double pointToSegmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx, projY = y1 + t * dy;
        double ddx = px - projX, ddy = py - projY;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }

    // ------------------------------------------------------------------ //
    // Packet animation feed - called by the app on each simulation event
    // ------------------------------------------------------------------ //

    /** Register that a packet just started traveling from->to; it will be drawn interpolating between them until updated again or removed. */
    public void onPacketHop(Packet packet, String fromId, String toId) {
        PacketAnim anim = animatedPackets.computeIfAbsent(packet.getId(), k -> new PacketAnim());
        anim.progress = 0.0;
        anim.fromId = fromId;
        anim.toId = toId;
        anim.color = colorForPriority(packet.getPriority().ordinal());
        anim.packet = packet;
    }

    public void onPacketFinished(Packet packet) {
        animatedPackets.remove(packet.getId());
    }

    /** Advance all in-flight animation progress; call from the app's render/animation timer. */
    public void advanceAnimations(double deltaFraction) {
        Iterator<PacketAnim> it = animatedPackets.values().iterator();
        while (it.hasNext()) {
            PacketAnim a = it.next();
            a.progress += deltaFraction;
            if (a.progress > 1.0) a.progress = 1.0;
        }
    }

    private Color colorForPriority(int ordinal) {
        return switch (ordinal) {
            case 0 -> Color.web("#ff5c5c"); // Voice
            case 1 -> Color.web("#ffb443"); // Video
            case 2 -> Color.web("#4fc3f7"); // Web
            case 3 -> Color.web("#81c784"); // Email
            default -> Color.web("#b39ddb"); // File Transfer
        };
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    public void redraw() {
        // TODO: this redraws every router/link/packet from scratch on every frame.
        // Fine up to the ~15-router topologies this project ships with; if someone
        // generates a much larger network (a few hundred routers), this will likely
        // start costing real frame time. Worth revisiting with dirty-region tracking
        // or an off-screen buffer if that ever becomes a real use case.
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.web("#0f1420"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        if (topology == null) return;

        for (Link link : topology.getLinks()) {
            drawLink(gc, link);
        }
        for (Router router : topology.getRouters()) {
            drawRouter(gc, router);
        }
        for (PacketAnim anim : animatedPackets.values()) {
            drawPacket(gc, anim);
        }
    }

    private void drawLink(GraphicsContext gc, Link link) {
        Router a = topology.getRouter(link.getRouterAId());
        Router b = topology.getRouter(link.getRouterBId());
        if (a == null || b == null) return;

        boolean selected = link == selectedLink;
        if (!link.isUp()) {
            gc.setStroke(Color.web("#5c2020"));
            gc.setLineDashes(6, 6);
        } else {
            double util = Math.min(1.0, link.getUtilization());
            Color base = Color.color(0.25 + util * 0.6, 0.55 - util * 0.35, 0.85 - util * 0.5);
            gc.setStroke(selected ? Color.web("#ffffff") : base);
            gc.setLineDashes(0);
        }
        gc.setLineWidth(selected ? 4 : (1.5 + link.getUtilization() * 3));
        gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        gc.setLineDashes(0);

        double midX = (a.getX() + b.getX()) / 2;
        double midY = (a.getY() + b.getY()) / 2;
        gc.setFill(Color.web("#c9d6e3"));
        gc.setFont(Font.font(10));
        gc.fillText(String.format("c=%.0f", link.getCost()), midX + 4, midY - 4);
    }

    private void drawRouter(GraphicsContext gc, Router router) {
        boolean selected = router == selectedRouter;
        double occ = router.getQueueOccupancy();

        Color fill = router.isUp()
                ? Color.color(0.15 + occ * 0.6, 0.55 - occ * 0.35, 0.35)
                : Color.web("#3a1414");
        gc.setFill(fill);
        gc.fillOval(router.getX() - ROUTER_RADIUS, router.getY() - ROUTER_RADIUS, ROUTER_RADIUS * 2, ROUTER_RADIUS * 2);

        gc.setStroke(selected ? Color.WHITE : (router.isUp() ? Color.web("#8fd3ff") : Color.web("#a04b4b")));
        gc.setLineWidth(selected ? 3 : 2);
        gc.strokeOval(router.getX() - ROUTER_RADIUS, router.getY() - ROUTER_RADIUS, ROUTER_RADIUS * 2, ROUTER_RADIUS * 2);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.fillText(router.getLabel(), router.getX() - ROUTER_RADIUS + 2, router.getY() + 4);

        gc.setFont(Font.font(9));
        gc.setFill(Color.web("#c9d6e3"));
        gc.fillText(router.getQueueSize() + "/" + router.getQueueCapacity(), router.getX() - 14, router.getY() + ROUTER_RADIUS + 12);
    }

    private void drawPacket(GraphicsContext gc, PacketAnim anim) {
        Router a = topology.getRouter(anim.fromId);
        Router b = topology.getRouter(anim.toId);
        if (a == null || b == null) return;
        double x = a.getX() + (b.getX() - a.getX()) * anim.progress;
        double y = a.getY() + (b.getY() - a.getY()) * anim.progress;

        gc.setFill(anim.color);
        gc.fillOval(x - 5, y - 5, 10, 10);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(x - 5, y - 5, 10, 10);
    }
}
