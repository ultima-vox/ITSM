# Kubernetes production deployment

`deploy/kubernetes` is hardened Kustomize base for application tier. PostgreSQL, Redis,
RabbitMQ, OpenSearch, object storage, Keycloak, ingress controller, metrics-server, and
certificate controller are managed platform dependencies and are not installed by this base.

Before deployment:

1. Mirror backend/frontend images to trusted registry and address them by immutable digest.
2. Patch `ConfigMap` endpoints, CORS origin, ingress hostname, TLS secret, and ingress namespace.
3. Create `itsm-secrets` through External Secrets/CSI/KMS. Never apply `secret.example.yaml`.
4. Confirm PostgreSQL backup and restore drill, then run migration job or allow first backend
   replica to run Flyway before scaling rollout.

Example render and image substitution:

```bash
cd deploy/kubernetes
kustomize edit set image \
  vox-itsm-backend=registry.example/vox-itsm-backend@sha256:BACKEND_DIGEST \
  vox-itsm-frontend=registry.example/vox-itsm-frontend@sha256:FRONTEND_DIGEST
kustomize build . > rendered.yaml
kubectl apply --server-side -f rendered.yaml
kubectl -n vox-itsm rollout status deployment/backend
kubectl -n vox-itsm rollout status deployment/frontend
```

Base provides rolling updates, two replicas, startup/readiness/liveness probes, resource
requests/limits, HPA, PDB, non-root containers, read-only root filesystems, dropped Linux
capabilities, disabled service-account tokens, TLS ingress, and default-deny ingress policy.
