# Flux Kubernetes Deployment

This directory provides deployment-ready Kubernetes manifests for Flux with:
- Infra: `redis`, `zookeeper`, `kafka`, `keycloak`, `postgres`
- Apps: `flux-config-server`, `discovery-service`, `gateway-service`, `user-management-service`, `calendars-service`, `flux-frontend`
- Ingress routing for web and API hosts
- Auto-generated `ConfigMap` and `Secret` from your `.env`

## 1) Set image tags (GitLab)

Use your own GitLab images before deploy:

```bash
cp k8s/.env.k8s.example k8s/.env.k8s
# Edit k8s/.env.k8s with your image URLs
set -a
source k8s/.env.k8s
set +a
```

Required image vars:
- `CONFIG_SERVER_IMAGE`
- `DISCOVERY_IMAGE`
- `GATEWAY_IMAGE`
- `USER_MANAGEMENT_IMAGE`
- `CALENDAR_IMAGE`
- `FRONTEND_IMAGE`

## 2) Provide application env values

Keep your real env values in project `.env`.

`k8s/scripts/generate-env-manifests.sh` will:
- split non-sensitive vars into `ConfigMap`
- split sensitive vars into `Secret`
- add Kubernetes internal defaults if missing (e.g. `kafka:9092`, `redis`)

## 3) Deploy

```bash
./k8s/scripts/deploy.sh ./.env
```

## 4) Verify

```bash
kubectl get pods -n flux
kubectl get svc -n flux
kubectl get ingress -n flux
```

## Notes

- Default namespace is `flux` (`K8S_NAMESPACE` can override).
- Default ingress hosts are `flux.local` (frontend) and `api.flux.local` (gateway).
- Add host entries to test locally with ingress controller.
- Frontend reads API base URL from `VITE_API_BASE_URL` at build time.
