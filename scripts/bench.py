#!/usr/bin/env python3
"""Minimal HTTP load generator: fixed concurrency, keep-alive connections, latency percentiles.

    python3 scripts/bench.py --url 'http://localhost:8082/search?q=laptop' --requests 2000 --concurrency 20 \
        [--header 'X-User-Id: 1'] [--urls-file urls.txt] [--label 'MySQL LIKE']

Per-request headers can be put in a URL fragment, e.g. http://localhost:8083/cart#X-User-Id=7

Prints one JSON line: label, requests, errors, throughput (req/s) and mean/p50/p95/p99 latency (ms).
A warm-up of 5% of the requests (min 50) is run first and excluded from the numbers.
"""
import argparse
import http.client
import json
import statistics
import threading
import time
from itertools import cycle
from urllib.parse import urlsplit

def worker(urls, headers, count, latencies, errors, lock):
    conns = {}
    for url in urls[:count] if len(urls) >= count else [u for _, u in zip(range(count), cycle(urls))]:
        parts = urlsplit(url)
        key = parts.netloc
        if key not in conns:
            conns[key] = http.client.HTTPConnection(parts.hostname, parts.port, timeout=30)
        path = parts.path + ("?" + parts.query if parts.query else "")
        # optional per-request headers in the fragment: http://host/cart#X-User-Id=7
        request_headers = dict(headers)
        if parts.fragment:
            request_headers.update(kv.split("=", 1) for kv in parts.fragment.split("&"))
        start = time.perf_counter()
        try:
            conns[key].request("GET", path, headers=request_headers)
            resp = conns[key].getresponse()
            resp.read()
            ok = 200 <= resp.status < 300
        except Exception:
            ok = False
            conns[key].close()
            conns[key] = http.client.HTTPConnection(parts.hostname, parts.port, timeout=30)
        elapsed = (time.perf_counter() - start) * 1000
        with lock:
            if ok:
                latencies.append(elapsed)
            else:
                errors.append(elapsed)

def run(urls, headers, total, concurrency):
    latencies, errors, lock = [], [], threading.Lock()
    per = [total // concurrency + (1 if i < total % concurrency else 0) for i in range(concurrency)]
    offset, threads = 0, []
    for n in per:
        # each worker gets its own slice of the URL list so workers don't all hit the same URL
        mine = [urls[(offset + k) % len(urls)] for k in range(n)]
        offset += n
        threads.append(threading.Thread(target=worker, args=(mine, headers, n, latencies, errors, lock)))
    start = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    return latencies, errors, time.perf_counter() - start

def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url")
    ap.add_argument("--urls-file")
    ap.add_argument("--requests", type=int, default=1000)
    ap.add_argument("--concurrency", type=int, default=10)
    ap.add_argument("--header", action="append", default=[])
    ap.add_argument("--label", default="")
    a = ap.parse_args()
    urls = [a.url] if a.url else [l.strip() for l in open(a.urls_file) if l.strip()]
    headers = dict(h.split(": ", 1) for h in a.header)
    run(urls, headers, max(50, a.requests // 20), a.concurrency)  # warm-up
    lat, err, secs = run(urls, headers, a.requests, a.concurrency)
    print(json.dumps({
        "label": a.label, "requests": a.requests, "concurrency": a.concurrency, "errors": len(err),
        "throughput_rps": round(len(lat) / secs, 1),
        "mean_ms": round(statistics.mean(lat), 2) if lat else None,
        "p50_ms": round(pct(lat, 50), 2) if lat else None,
        "p95_ms": round(pct(lat, 95), 2) if lat else None,
        "p99_ms": round(pct(lat, 99), 2) if lat else None,
    }))

if __name__ == "__main__":
    main()
