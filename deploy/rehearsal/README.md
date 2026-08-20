# Rehearsal overlay

Runs the production manifests against dependencies inside the cluster, so the deployment can
be exercised without managed PostgreSQL, RabbitMQ or an identity provider. **Not a production
overlay**: storage is ephemeral, credentials are in plain text, and Keycloak runs in dev mode.

```bash
kind create cluster --name vox-itsm
docker build -t vox-itsm-backend:rehearsal backend
docker build -t vox-itsm-frontend:rehearsal frontend
kind load docker-image vox-itsm-backend:rehearsal vox-itsm-frontend:rehearsal --name vox-itsm

kubectl create namespace vox-itsm
kubectl -n vox-itsm create configmap itsm-realm \
  --from-file=itsm-realm.json=infra/keycloak/itsm-realm.json
kubectl apply -k deploy/rehearsal

kubectl -n vox-itsm rollout status deployment/backend
kubectl -n vox-itsm port-forward svc/frontend 8088:8080
```
