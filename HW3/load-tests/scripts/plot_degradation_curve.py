#!/usr/bin/env python3
"""
Build a stress/baseline degradation-style chart from sampled ACK latency CSVs.
X = progress through the phase (0–100%). Y = p95 latency (ms) per bin.
Outputs SVG (+ optional PNG if matplotlib is installed).
"""
from __future__ import annotations

import csv
import math
import sys
from pathlib import Path


def read_samples(path: Path) -> list[tuple[int, int]]:
    rows: list[tuple[int, int]] = []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                rows.append((int(row["timestamp"]), int(row["latency"])))
            except (KeyError, ValueError):
                continue
    rows.sort(key=lambda x: x[0])
    return rows


def p95(values: list[int]) -> float:
    if not values:
        return float("nan")
    s = sorted(values)
    i = int(0.95 * (len(s) - 1))
    return float(s[i])


def bin_p95_over_phase(
    rows: list[tuple[int, int]], num_bins: int = 40
) -> tuple[list[float], list[float]]:
    """Returns (x_percent_midpoints, p95_ms_per_bin). Skips empty bins for line continuity."""
    if len(rows) < 2:
        return [], []

    t0, t1 = rows[0][0], rows[-1][0]
    span = max(t1 - t0, 1)
    bins: list[list[int]] = [[] for _ in range(num_bins)]
    for ts, lat in rows:
        p = (ts - t0) / span
        idx = min(num_bins - 1, int(p * num_bins))
        bins[idx].append(lat)

    xs: list[float] = []
    ys: list[float] = []
    for i, bucket in enumerate(bins):
        if not bucket:
            continue
        xs.append((i + 0.5) / num_bins * 100.0)
        ys.append(p95(bucket))
    return xs, ys


def svg_line_chart(
    series: list[tuple[str, str, list[float], list[float]]],
    title: str,
    width: int = 900,
    height: int = 420,
) -> str:
    margin_l, margin_r, margin_t, margin_b = 72, 40, 56, 52
    plot_w = width - margin_l - margin_r
    plot_h = height - margin_t - margin_b

    all_y = [y for _, _, xs, ys in series for y in ys if not math.isnan(y)]
    y_max = max(all_y) * 1.08 if all_y else 100.0
    y_max = max(y_max, 1.0)

    def x_px(xp: float) -> float:
        return margin_l + (xp / 100.0) * plot_w

    def y_px(yv: float) -> float:
        return margin_t + plot_h - (yv / y_max) * plot_h

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        '<style>text{font-family:system-ui,-apple-system,sans-serif;font-size:13px;}'
        ".axis{stroke:#333;stroke-width:1}.grid{stroke:#ddd;stroke-width:1}</style>",
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="600">'
        f"{escape_xml(title)}</text>",
        f'<text x="{width // 2}" y="{height - 12}" text-anchor="middle" fill="#555">'
        "Phase progress (%)</text>",
        f'<text transform="translate(20,{margin_t + plot_h // 2}) rotate(-90)" '
        f'text-anchor="middle" fill="#555">p95 ACK latency (ms)</text>',
    ]

    # Y grid + labels
    for i in range(6):
        yv = y_max * i / 5
        yy = y_px(yv)
        parts.append(
            f'<line class="grid" x1="{margin_l}" y1="{yy:.1f}" '
            f'x2="{margin_l + plot_w}" y2="{yy:.1f}"/>'
        )
        parts.append(
            f'<text x="{margin_l - 8}" y="{yy + 4}" text-anchor="end" fill="#666">{yv:.0f}</text>'
        )

    # X grid
    for pct in (0, 25, 50, 75, 100):
        xx = x_px(pct)
        parts.append(
            f'<line class="grid" x1="{xx:.1f}" y1="{margin_t}" '
            f'x2="{xx:.1f}" y2="{margin_t + plot_h}"/>'
        )
        parts.append(
            f'<text x="{xx:.1f}" y="{margin_t + plot_h + 22}" text-anchor="middle" fill="#666">'
            f"{pct}</text>"
        )

    parts.append(
        f'<rect x="{margin_l}" y="{margin_t}" width="{plot_w}" height="{plot_h}" '
        'fill="none" stroke="#333"/>'
    )

    colors = ["#1f77b4", "#d62728", "#2ca02c", "#9467bd"]
    for idx, (label, color, xs, ys) in enumerate(series):
        c = color or colors[idx % len(colors)]
        if len(xs) < 2:
            continue
        d = "M " + " L ".join(f"{x_px(x):.1f},{y_px(y):.1f}" for x, y in zip(xs, ys))
        parts.append(f'<path d="{d}" fill="none" stroke="{c}" stroke-width="2.5"/>')
        # legend
        ly = margin_t + 20 + idx * 20
        parts.append(f'<rect x="{margin_l + plot_w - 160}" y="{ly - 10}" width="12" height="12" fill="{c}"/>')
        parts.append(
            f'<text x="{margin_l + plot_w - 142}" y="{ly}" fill="#333">{escape_xml(label)}</text>'
        )

    parts.append("</svg>")
    return "\n".join(parts)


def escape_xml(s: str) -> str:
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    results = root / "results"
    out_dir = results
    out_dir.mkdir(parents=True, exist_ok=True)

    main_lat = results / "MAIN-latency.csv"
    base_lat = results / "BASELINE-latency.csv"

    series_data: list[tuple[str, str, list[float], list[float]]] = []
    if base_lat.is_file():
        bx, by = bin_p95_over_phase(read_samples(base_lat))
        series_data.append(("BASELINE (500k phase)", "#d62728", bx, by))
    if main_lat.is_file():
        mx, my = bin_p95_over_phase(read_samples(main_lat))
        series_data.append(("MAIN stress (1M phase)", "#1f77b4", mx, my))

    if len(series_data) < 1:
        print("No latency CSVs found under results/", file=sys.stderr)
        return 1

    title = "End-to-end ACK latency vs phase progress (p95 per bin)"
    svg = svg_line_chart(series_data, title)
    svg_path = out_dir / "fig-degradation-latency-p95.svg"
    svg_path.write_text(svg, encoding="utf-8")
    print(f"Wrote {svg_path}")

    # Optional PNG for Word/PDF users
    try:
        import matplotlib.pyplot as plt

        plt.figure(figsize=(10, 4.5))
        for label, color, xs, ys in series_data:
            if len(xs) >= 2:
                plt.plot(xs, ys, label=label, color=color, linewidth=2)
        plt.xlabel("Phase progress (%)")
        plt.ylabel("p95 latency (ms)")
        plt.title(title)
        plt.grid(True, alpha=0.3)
        plt.legend(loc="upper left")
        plt.tight_layout()
        png_path = out_dir / "fig-degradation-latency-p95.png"
        plt.savefig(png_path, dpi=150)
        plt.close()
        print(f"Wrote {png_path}")
    except ImportError:
        print("(matplotlib not installed; SVG only. pip install matplotlib for PNG.)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
