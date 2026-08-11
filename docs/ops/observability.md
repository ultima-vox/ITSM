# Production observability

Scrape backend `/actuator/prometheus` over internal authenticated networking. Import
`deploy/observability/grafana-dashboard.json` and load
`deploy/observability/prometheus-rules.yml` into Prometheus-compatible ruler. Route
`critical` alerts to paging and `warning` alerts to service owners.

Backend uses Micrometer Tracing over OpenTelemetry. Production enables W3C propagation and
exports OTLP/HTTP spans to `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`; local environments keep
export disabled while still generating trace/span correlation in-process. Set
`OTEL_TRACES_SAMPLER_ARG` between `0.0` and `1.0` based on traffic and retention budget.
Production console logs use ECS JSON and include trace/span IDs plus `X-Correlation-ID`.

Collector must receive OTLP on HTTP port 4318 and export to organization trace storage.
Alert when collector export failures or dropped spans increase; never put ticket bodies,
attachment names, tokens, or user-entered values into span tags.

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
