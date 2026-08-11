# Performance regression baseline — 2026-08-11

This is a reproducible local regression baseline, not a production capacity certification. Full k6 output is retained in [`docs/evidence/performance-summary-2026-08-11.json`](../evidence/performance-summary-2026-08-11.json).

## Environment and dataset

- Windows 11 Pro, Intel Xeon E5-2680 v4, 31.8 GiB RAM, Docker Engine 29.6.2.
- Java 25.0.4 LTS; k6 1.2.3 container.
- Application: `dev,compose` profiles, one local JVM, PostgreSQL 17, Redis 7, RabbitMQ 4, OpenSearch 2.19.1, MinIO, Keycloak 26.2.
- Dataset before measured run: 66 work items, 3 catalog items, 6 CIs, 17 notifications, 102 outbox events. Workload adds isolated timestamped records.
- 10 concurrent scenarios, maximum 27 allocated VUs: read paths use 4 VUs, writes use 2 VUs, login/index/bulk paths use 1 VU. 74 scenario iterations, 117 HTTP requests, 1.90 s measured wall time.
- Keycloak receives one unmeasured warm-up request. This removes JVM/provider cold start from steady-state login SLO. OpenSearch metric includes polling until new ticket becomes visible.

## Reproduction

Start compose dependencies and backend on port 8080, then run:

```powershell
docker run --rm --add-host host.docker.internal:host-gateway `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e OIDC_TOKEN_URL=http://host.docker.internal:8081/realms/itsm/protocol/openid-connect/token `
  -e ITERATIONS=10 -e READ_VUS=4 -e WRITE_VUS=2 `
  -v "${PWD}\scripts:/scripts:ro" `
  -v "${PWD}\backend\build\performance:/results" `
  grafana/k6:1.2.3 run /scripts/load-smoke.js
```

Thresholds live beside workload in `scripts/load-smoke.js`; any failed check, error rate >=1%, global p95 >=1 s, or global p99 >=2 s fails command.

## Results

All 86 checks passed. HTTP error rate: 0%. Throughput: 61.47 requests/s. Global HTTP latency: average 66.76 ms, p95 133.11 ms, p99 160.61 ms, max 164.39 ms.

| Scenario | Average ms | p95 ms | p99 ms | Max ms |
|---|---:|---:|---:|---:|
| Login | 74.50 | 102.70 | 107.74 | 109 |
| Ticket list | 67.80 | 114.70 | 126.94 | 130 |
| Ticket creation | 62.80 | 94.60 | 95.72 | 96 |
| Ticket transition | 61.40 | 88.80 | 88.96 | 89 |
| Catalog search | 59.60 | 75.10 | 75.82 | 76 |
| Global search | 60.80 | 93.25 | 105.85 | 109 |
| CMDB traversal, 3 hops | 89.80 | 145.75 | 147.55 | 148 |
| Dashboard report | 111.10 | 174.20 | 175.64 | 176 |
| Notification dispatch acceptance | 49.60 | 80.40 | 80.88 | 81 |
| Bulk client import, 10 records | 153.00 | 190.80 | 194.16 | 195 |
| OpenSearch indexing visibility | 842.00 | 851.90 | 852.78 | 853 |

## Resource snapshots

Snapshots are before/after observations, not peak telemetry. Application JVM CPU time increased 1.98 s during first successful run; working set increased from 292.3 MiB to 295.8 MiB; private bytes from 359.5 MiB to 362.0 MiB. After-run compose snapshots: PostgreSQL 5.11% CPU / 104.2 MiB, OpenSearch 7.44% / 1.55 GiB, Redis 4.29% / 4.65 MiB, Keycloak 0.20% / 708.2 MiB, RabbitMQ 0.22% / 144.8 MiB, MinIO 0.07% / 194.0 MiB.

For capacity decisions, run longer constant-arrival-rate stages on production-equivalent hosts, capture Micrometer/Prometheus time series, and test production-size data. This baseline gates regressions only.
