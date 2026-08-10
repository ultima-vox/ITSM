# Production observability

Scrape backend `/actuator/prometheus` over internal authenticated networking. Import
`deploy/observability/grafana-dashboard.json` and load
`deploy/observability/prometheus-rules.yml` into Prometheus-compatible ruler. Route
`critical` alerts to paging and `warning` alerts to service owners.

## Backend unavailable

Check deployment readiness, recent rollout, pod events, PostgreSQL reachability, and ingress.
Rollback latest image if failures started with deployment. Escalate after two failed replicas.

## High HTTP error rate

Split 5xx by `uri`, inspect correlation IDs in structured logs, then check DB pool and downstream
health. Roll back correlated release; preserve request samples for incident review.

## High HTTP latency

Split p95 by `uri`. Check DB pool, slow queries, JVM pause time, OpenSearch, and RabbitMQ backlog.
Scale only after identifying saturated resource; scaling cannot repair lock contention.

## JVM heap pressure

Capture heap histogram and GC metrics before restart. Check traffic and recent allocations.
Restart one replica at a time only when memory pressure threatens availability.

## Database pool saturation

Check active vs pending connections, PostgreSQL session limits, slow queries, and locks. Do not
raise pool size without confirming DB capacity. Cancel only identified runaway queries.
