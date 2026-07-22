import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

BG = "#0b0f18"
PANEL = "#141b29"
BORDER = "#33415c"
TEXT = "#e7eef7"
SUBTEXT = "#93a6bf"
BLUE = "#4fc3f7"
GREEN = "#43cf94"
ORANGE = "#ffb443"
PURPLE = "#b39ddb"
RED = "#ff6f6f"

fig, ax = plt.subplots(figsize=(15, 10.5), dpi=160)
fig.patch.set_facecolor(BG)
ax.set_facecolor(BG)
ax.set_xlim(0, 1500)
ax.set_ylim(0, 1050)
ax.axis("off")

def box(x, y, w, h, label, sublabel=None, color=BORDER, fill=PANEL, fontsize=13, sub_fontsize=10, bold=True, text_color=TEXT):
    b = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=10",
                        linewidth=1.8, edgecolor=color, facecolor=fill, zorder=3)
    ax.add_patch(b)
    if sublabel:
        ax.text(x + w/2, y + h*0.62, label, ha="center", va="center", color=text_color,
                 fontsize=fontsize, fontweight="bold" if bold else "normal", zorder=4)
        ax.text(x + w/2, y + h*0.30, sublabel, ha="center", va="center", color=SUBTEXT,
                 fontsize=sub_fontsize, zorder=4, wrap=True)
    else:
        ax.text(x + w/2, y + h/2, label, ha="center", va="center", color=text_color,
                 fontsize=fontsize, fontweight="bold" if bold else "normal", zorder=4)
    return (x, y, w, h)

def arrow(x1, y1, x2, y2, color=BLUE, style="-|>", lw=1.8, connectionstyle="arc3,rad=0.0"):
    a = FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=style, mutation_scale=14,
                         color=color, linewidth=lw, zorder=2, connectionstyle=connectionstyle)
    ax.add_patch(a)

def section_label(x, y, text, color=SUBTEXT):
    ax.text(x, y, text, ha="left", va="center", color=color, fontsize=11.5,
             fontweight="bold", zorder=4, family="monospace")

# ---------------------------------------------------------------- #
# Title
# ---------------------------------------------------------------- #
ax.text(750, 1010, "NetSimX — Module Architecture", ha="center", va="center",
         color=TEXT, fontsize=22, fontweight="bold")
ax.text(750, 978, "Java 17 + JavaFX  ·  package com.netsimx", ha="center", va="center",
         color=SUBTEXT, fontsize=12)

# ---------------------------------------------------------------- #
# Layer 1: GUI (Module 12)
# ---------------------------------------------------------------- #
section_label(40, 905, "gui/  —  Interactive Dashboard (Module 12)")
gui_outer = box(40, 800, 1420, 95, "", fill="#111826", color="#2a3650")
gui_boxes = [
    ("NetSimXApp", "wires everything"),
    ("TopologyCanvas", "render + edit + animate"),
    ("ChartsPanel", "live metrics"),
    ("ControlPanel", "controls"),
    ("LogConsole", "event log"),
]
gx = 60
gw = (1420 - 40) / len(gui_boxes)
gui_centers = []
for name, sub in gui_boxes:
    box(gx, 815, gw - 20, 65, name, sub, color=BLUE, fill="#152233", fontsize=12, sub_fontsize=9)
    gui_centers.append(gx + (gw - 20) / 2)
    gx += gw

# ---------------------------------------------------------------- #
# Layer 2: Simulation Engine (core orchestrator)
# ---------------------------------------------------------------- #
section_label(40, 755, "simulation/  —  Tick-driven Simulation Core (Modules 2, 4, 5, 6, 7, 8)")
engine_x, engine_y, engine_w, engine_h = 480, 590, 540, 140
box(engine_x, engine_y, engine_w, engine_h, "SimulationEngine", None, color=ORANGE, fill="#241d12", fontsize=16)
sub_items = ["TrafficGenerator", "QoSScheduler", "CongestionController",
             "QueueManager", "FailureSimulator", "TcpUdpManager"]
cols = 3
sw, sh = 168, 34
start_x = engine_x + (engine_w - (cols*sw + (cols-1)*8)) / 2
start_y = engine_y + engine_h - 60
for i, item in enumerate(sub_items):
    r, c = divmod(i, cols)
    sx = start_x + c * (sw + 8)
    sy = start_y - r * (sh + 8)
    box(sx, sy, sw, sh, item, None, color="#5a4520", fill="#2c2413", fontsize=9.2, bold=False)

# arrow: GUI -> Engine
arrow(750, 800, 750, 733, color=BLUE)

# ---------------------------------------------------------------- #
# Layer 3: Model / Routing / AI  (Modules 1, 3, 9, 10)
# ---------------------------------------------------------------- #
section_label(40, 555, "model/, routing/, ai/  —  Topology, Path-finding & Adaptive Optimization (Modules 1, 3, 9, 10)")

model_box = box(60, 430, 380, 100, "model", "NetworkTopology · Router · Link\nPacket · PacketPriority · Protocol",
                 color=GREEN, fill="#0f2a1e", fontsize=13, sub_fontsize=9.5)

routing_box = box(500, 430, 460, 100, "routing", "DijkstraRouting (OSPF) · BellmanFordRouting (RIP)\nECMPRouting (load balancing) · RoutingTable",
                   color=PURPLE, fill="#221c33", fontsize=13, sub_fontsize=9.5)

ai_box = box(1020, 430, 440, 100, "ai", "QLearningRouteOptimizer\n(implements RoutingAlgorithm — drop-in\nalternative to Dijkstra/BF/ECMP)",
             color=RED, fill="#2b1717", fontsize=13, sub_fontsize=9.5)

# arrows: Engine -> Model / Routing / AI
arrow(engine_x + 40, engine_y, 250, 530, color=GREEN, connectionstyle="arc3,rad=-0.15")
arrow(engine_x + engine_w/2, engine_y, 730, 530, color=PURPLE)
arrow(engine_x + engine_w - 40, engine_y, 1240, 530, color=RED, connectionstyle="arc3,rad=0.15")
# routing/ai both implement RoutingAlgorithm -> shown as a dashed link
arrow(960, 480, 1020, 480, color=SUBTEXT, style="-", lw=1.2, connectionstyle="arc3,rad=0")
ax.text(990, 495, "same\ninterface", ha="center", va="center", color=SUBTEXT, fontsize=7.5, style="italic")

# ---------------------------------------------------------------- #
# Layer 4: Analytics & Persistence (Modules 11, 13)
# ---------------------------------------------------------------- #
section_label(40, 355, "analytics/, persistence/  —  Metrics & Digital Twin I/O (Modules 11, 13)")

analytics_box = box(60, 220, 660, 100, "analytics", "StatisticsCollector  →  PerformanceSnapshot\n(throughput, delay, PDR, loss rate, utilization — feeds ChartsPanel)",
                     color=BLUE, fill="#0e2230", fontsize=13, sub_fontsize=10)

persistence_box = box(780, 220, 680, 100, "persistence", "MiniJson (dependency-free parser)  ·  TopologyIO (JSON import/export)\nCsvExporter (performance history → CSV)",
                       color=ORANGE, fill="#241d12", fontsize=13, sub_fontsize=9.5)

arrow(engine_x + 150, engine_y, 390, 320, color=BLUE, connectionstyle="arc3,rad=0.2")
arrow(1080, 800, 1120, 320, color=ORANGE, connectionstyle="arc3,rad=0.35")
ax.text(1225, 560, "GUI reads/writes\ntopology + exports stats", ha="left", va="center",
         color=SUBTEXT, fontsize=8.5, style="italic")

# ---------------------------------------------------------------- #
# Bottom note
# ---------------------------------------------------------------- #
ax.text(750, 60, "Every arrow is a real dependency in the source tree — this diagram mirrors the actual package structure, not an idealized one.",
         ha="center", va="center", color=SUBTEXT, fontsize=10, style="italic")

plt.tight_layout()
plt.savefig("/home/claude/netsimx/docs/assets/architecture-diagram.png", facecolor=BG, bbox_inches="tight", pad_inches=0.3)
print("saved")
