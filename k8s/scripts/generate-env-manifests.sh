#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT_DIR}/k8s"
ENV_FILE="${1:-${ROOT_DIR}/.env}"
NAMESPACE="${K8S_NAMESPACE:-flux}"
OUT_DIR="${K8S_DIR}/generated"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
TMP_CONFIG="$(mktemp)"
TMP_SECRET="$(mktemp)"
trap 'rm -f "${TMP_CONFIG}" "${TMP_SECRET}"' EXIT

awk -F= -v config_file="${TMP_CONFIG}" -v secret_file="${TMP_SECRET}" '
/^[[:space:]]*#/ || /^[[:space:]]*$/ {next}
{
  key=$1
  if (key ~ /(PASSWORD|SECRET|TOKEN|JWT|CREDENTIAL|(^|_)KEY($|_)|PASS)/) {
    print $0 >> secret_file
  } else {
    print $0 >> config_file
  }
}
' "${ENV_FILE}"

# Kubernetes internal defaults, only when missing from .env
append_if_missing() {
  local file="$1"
  local key="$2"
  local value="$3"
  if ! grep -q "^${key}=" "$file"; then
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

append_if_missing "${TMP_CONFIG}" "CONFIG_URL" "http://flux-config-server:8888"
append_if_missing "${TMP_CONFIG}" "EUREKA_SERVICE_URL" "http://discovery-service:8761/eureka"
append_if_missing "${TMP_CONFIG}" "REDIS_HOST" "redis"
append_if_missing "${TMP_CONFIG}" "KAFKA_BOOTSTRAP_SERVERS" "kafka:9092"
append_if_missing "${TMP_CONFIG}" "ISSUER_URL" "http://keycloak:8080/realms/flux"
append_if_missing "${TMP_CONFIG}" "JWT_SET_URL" "http://keycloak:8080/realms/flux/protocol/openid-connect/certs"
append_if_missing "${TMP_CONFIG}" "APP_JWT_URL" "http://gateway-service:8083"
append_if_missing "${TMP_CONFIG}" "EUREKA_HOSTNAME" "discovery-service"
append_if_missing "${TMP_CONFIG}" "CONFIG_PORT" "8888"
append_if_missing "${TMP_CONFIG}" "DISCOVERY_PORT" "8761"
append_if_missing "${TMP_CONFIG}" "GATEWAY_PORT" "8083"
append_if_missing "${TMP_CONFIG}" "USER_MANAGEMENT_PORT" "8081"
append_if_missing "${TMP_CONFIG}" "CALENDARS_PORT" "8082"
append_if_missing "${TMP_CONFIG}" "POSTGRES_DB" "flux"
append_if_missing "${TMP_CONFIG}" "POSTGRES_USER" "postgres"
append_if_missing "${TMP_SECRET}" "POSTGRES_PASSWORD" "m12345"
append_if_missing "${TMP_SECRET}" "KEYCLOAK_ADMIN" "admin"
append_if_missing "${TMP_SECRET}" "KEYCLOAK_ADMIN_PASSWORD" "admin"

kubectl create configmap flux-app-config \
  --namespace "${NAMESPACE}" \
  --from-env-file="${TMP_CONFIG}" \
  --dry-run=client -o yaml > "${OUT_DIR}/01-flux-app-configmap.yaml"

kubectl create secret generic flux-app-secrets \
  --namespace "${NAMESPACE}" \
  --from-env-file="${TMP_SECRET}" \
  --dry-run=client -o yaml > "${OUT_DIR}/02-flux-app-secret.yaml"

echo "Generated ${OUT_DIR}/01-flux-app-configmap.yaml"
echo "Generated ${OUT_DIR}/02-flux-app-secret.yaml"
