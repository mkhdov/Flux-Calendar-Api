#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT_DIR}/k8s"
OUT_DIR="${K8S_DIR}/generated"
ENV_FILE="${1:-${ROOT_DIR}/.env}"

"${K8S_DIR}/scripts/render-manifests.sh"
"${K8S_DIR}/scripts/generate-env-manifests.sh" "${ENV_FILE}"

kubectl apply -f "${OUT_DIR}/00-namespace.yaml"
kubectl apply -f "${OUT_DIR}/01-flux-app-configmap.yaml"
kubectl apply -f "${OUT_DIR}/02-flux-app-secret.yaml"

for file in \
  10-redis.yaml \
  11-zookeeper.yaml \
  12-kafka.yaml \
  13-keycloak.yaml \
  14-postgres.yaml \
  20-config-server.yaml \
  21-discovery-service.yaml \
  22-user-management-service.yaml \
  23-calendars-service.yaml \
  24-gateway-service.yaml \
  25-frontend.yaml \
  30-ingress.yaml; do
  kubectl apply -f "${OUT_DIR}/${file}"
done

echo "Deployment applied."
