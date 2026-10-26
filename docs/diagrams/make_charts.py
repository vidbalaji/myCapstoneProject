#!/usr/bin/env python3
"""Draws the checkout sequence diagram and the benchmark charts (from docs/results/*.json).

    python3 docs/diagrams/make_charts.py      (needs matplotlib)
"""
import json
import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RESULTS = os.path.join(HERE, "..", "results")
BEFORE, AFTER = "#eb6834", "#2a78d6"   # categorical slots 2 and 1 (validated pair)
INK, MUTED, GRID = "#0b0b0b", "#52514e", "#e4e3df"
plt.rcParams.update({"font.family": "Liberation Sans", "font.size": 10, "text.color": INK,
                     "axes.labelcolor": MUTED, "xtick.color": MUTED, "ytick.color": MUTED})


def load(name):
    with open(os.path.join(RESULTS, name)) as f:
        return json.load(f)


COMPARISONS = [
    ("Product search\n200k products", "MySQL LIKE", load("bench-search-mysql.json"), "Elasticsearch", load("bench-search-es.json")),
    ("Order history\n300k orders", "No index", load("bench-orders-noindex.json"), "Composite index", load("bench-orders-index-nplus1.json")),
    ("Order history\nitem loading", "N+1 (12 queries)", load("bench-orders-index-nplus1.json"), "@BatchSize (3 queries)", load("bench-orders-index-batch.json")),
    ("Cart read", "MongoDB only", load("bench-cart-mongo.json"), "Redis cache", load("bench-cart-redis.json")),
    ("Gateway auth", "Validate every call", load("bench-gateway-auth-uncached.json"), "Cached in Redis", load("bench-gateway-auth-cached.json")),
]


def small_multiples(metric, unit, title, filename, fmt):
    fig, axes = plt.subplots(1, len(COMPARISONS), figsize=(13, 3.6))
    for ax, (name, b_label, b, a_label, a) in zip(axes, COMPARISONS):
        values = [b[metric], a[metric]]
        bars = ax.bar([0, 1], values, color=[BEFORE, AFTER], width=0.62, edgecolor="white", linewidth=2)
        for bar, v in zip(bars, values):
            ax.text(bar.get_x() + bar.get_width() / 2, v, fmt(v), ha="center", va="bottom", fontsize=9, color=INK)
        ax.set_xticks([0, 1], [b_label, a_label], fontsize=8)
        ax.set_title(name, fontsize=10, color=INK, fontweight="bold")
        ax.set_ylim(0, max(values) * 1.18)
        ax.grid(axis="y", color=GRID, linewidth=0.8)
        ax.set_axisbelow(True)
        for side in ("top", "right", "left"):
            ax.spines[side].set_visible(False)
        ax.spines["bottom"].set_color(MUTED)
        ax.tick_params(axis="y", labelsize=8, length=0)
    axes[0].set_ylabel(unit)
    handles = [plt.Rectangle((0, 0), 1, 1, color=BEFORE), plt.Rectangle((0, 0), 1, 1, color=AFTER)]
    fig.legend(handles, ["Before optimisation", "After optimisation"], loc="upper right", frameon=False, ncol=2)
    fig.suptitle(title, x=0.01, ha="left", fontsize=12, fontweight="bold")
    fig.tight_layout(rect=(0, 0, 1, 0.92))
    fig.savefig(os.path.join(HERE, filename), dpi=200)
    plt.close(fig)


def sequence():
    actors = ["Client", "API Gateway", "Order Svc", "Cart Svc", "Product Svc", "Kafka", "Payment Svc", "Notification"]
    msgs = [
        (0, 1, "POST /orders/checkout  (Bearer token)", False),
        (1, 1, "validate token (Redis cache / User Svc)", False),
        (1, 2, "forward + X-User-Id, X-User-Email", False),
        (2, 3, "GET /cart", False),
        (2, 4, "GET /products/{id}  (current price)", False),
        (2, 2, "save Order PAYMENT_PENDING (MySQL)", False),
        (2, 3, "DELETE /cart  (after commit)", False),
        (2, 5, "order-events: ORDER_PLACED", True),
        (2, 0, "201 Created  {orderId, status}", False),
        (5, 6, "ORDER_PLACED", True),
        (6, 6, "create payment link (Stripe / Razorpay)", False),
        (6, 5, "payment-events: PAYMENT_LINK_CREATED", True),
        (5, 2, "store paymentLink on order", True),
        (5, 7, "email: confirmation + payment link", True),
        (0, 6, "customer pays → gateway webhook (signed)", False),
        (6, 5, "payment-events: PAYMENT_SUCCEEDED", True),
        (5, 2, "order → PAID (status history)", True),
        (5, 7, "email: receipt", True),
    ]
    xs = [i * 1.6 for i in range(len(actors))]
    height = len(msgs) * 0.55 + 1.2
    fig, ax = plt.subplots(figsize=(13, height * 0.62))
    top = height
    for x, name in zip(xs, actors):
        ax.add_patch(plt.Rectangle((x - 0.62, top - 0.55), 1.24, 0.5, facecolor="#e8f1fc", edgecolor="#1c5cab"))
        ax.text(x, top - 0.3, name, ha="center", va="center", fontsize=9, fontweight="bold")
        ax.plot([x, x], [0.2, top - 0.55], color=GRID, linewidth=1.2, zorder=0)
    y = top - 1.0
    for i, (src, dst, label, is_async) in enumerate(msgs, start=1):
        color = "#4a3aa7" if is_async else INK
        style = "--" if is_async else "-"
        if src == dst:
            x = xs[src]
            ax.annotate("", xy=(x + 0.05, y - 0.22), xytext=(x + 0.05, y + 0.04),
                        arrowprops=dict(arrowstyle="-|>", color=color, connectionstyle="arc3,rad=-1.4", lw=1.1))
            ax.text(x + 0.35, y - 0.09, f"{i}. {label}", fontsize=8, va="center", color=color)
        else:
            ax.annotate("", xy=(xs[dst], y), xytext=(xs[src], y),
                        arrowprops=dict(arrowstyle="-|>", color=color, lw=1.1, linestyle=style))
            mid = (xs[src] + xs[dst]) / 2
            ax.text(mid, y + 0.08, f"{i}. {label}", fontsize=8, ha="center", va="bottom", color=color)
        y -= 0.55
    ax.set_xlim(-0.8, xs[-1] + 0.8)
    ax.set_ylim(0, top + 0.05)
    ax.axis("off")
    ax.text(-0.75, 0.25, "solid = synchronous HTTP      dashed = asynchronous Kafka event", fontsize=8, color=MUTED)
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, "checkout-sequence.png"), dpi=200)
    plt.close(fig)


if __name__ == "__main__":
    small_multiples("p50_ms", "median latency (ms)", "Median (p50) response time, lower is better",
                    "bench-latency.png", lambda v: f"{v:.2f} ms" if v < 10 else (f"{v:,.1f} ms" if v < 100 else f"{v:,.0f} ms"))
    small_multiples("throughput_rps", "requests / second", "Throughput at 10 concurrent clients, higher is better",
                    "bench-throughput.png", lambda v: f"{v:,.0f}" if v >= 10 else f"{v:.1f}")
    sequence()
    print("charts written to", HERE)
