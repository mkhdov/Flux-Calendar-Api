#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT_DIR}/k8s"
TEMPLATE_DIR="${K8S_DIR}/templates"
OUT_DIR="${K8S_DIR}/generated"

if ! command -v envsubst >/dev/null 2>&1; then
  echo "envsubst is required (install gettext)" >&2
  exit 1
fi

export K8S_NAMESPACE="${K8S_NAMESPACE:-flux}"
export INGRESS_CLASS="${INGRESS_CLASS:-nginx}"
export WEB_HOST="${WEB_HOST:-flux.local}"
export API_HOST="${API_HOST:-api.flux.local}"
export FRONTEND_CONTAINER_PORT="${FRONTEND_CONTAINER_PORT:-80}"

export REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
export ZOOKEEPER_IMAGE="${ZOOKEEPER_IMAGE:-confluentinc/cp-zookeeper:7.5.0}"
export KAFKA_IMAGE="${KAFKA_IMAGE:-confluentinc/cp-kafka:7.5.0}"
export KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:26.0.0}"
export POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16}"

required=(
  CONFIG_SERVER_IMAGE
  DISCOVERY_IMAGE
  GATEWAY_IMAGE
  USER_MANAGEMENT_IMAGE
  CALENDAR_IMAGE
  FRONTEND_IMAGE
)

missing=()
for var in "${required[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    missing+=("$var")
  fi
done

if (( ${#missing[@]} > 0 )); then
  printf 'Missing required image env vars: %s\n' "${missing[*]}" >&2
  echo "Example:" >&2
  echo "  export CONFIG_SERVER_IMAGE=registry.gitlab.com/group/flux-config:tag" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
for tpl in "${TEMPLATE_DIR}"/*.yaml; do
  out="${OUT_DIR}/$(basename "$tpl")"
  envsubst < "$tpl" > "$out"
done

echo "Rendered manifests in ${OUT_DIR}"
