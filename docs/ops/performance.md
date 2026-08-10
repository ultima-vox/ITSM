# Performance baseline

Run against isolated production-like environment after migrations and warm-up:

```bash
docker run --rm --network host \
  -e BASE_URL=http://127.0.0.1:8080 \
  -e VUS=10 -e DURATION=30s \
  -v "$PWD/scripts:/scripts:ro" \
  grafana/k6:1.2.3 run /scripts/load-smoke.js
```

Gate requires more than 99% checks, under 1% HTTP failures, p95 below 1 second, and p99 below
2 seconds. Use synthetic data and dedicated environment. Never load-test production without an
approved window, traffic ceiling, monitoring, and rollback owner.

This smoke baseline detects gross regressions; capacity certification must model expected
tenant count, concurrency, search load, attachment traffic, and event backlog.
