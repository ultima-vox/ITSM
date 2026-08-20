#!/usr/bin/env bash
# Writes the OpenSearch internal user database with bcrypt-hashed passwords.
# Passwords come from the environment and are never stored in the repository.
#
#   OPENSEARCH_ADMIN_PASSWORD=... OPENSEARCH_PASSWORD=... ./scripts/gen-opensearch-users.sh
set -euo pipefail

OUT_DIR="${OUT_DIR:-certs/opensearch}"
IMAGE="${OPENSEARCH_IMAGE:-opensearchproject/opensearch:2.19.1}"
SEARCH_USER="${OPENSEARCH_USERNAME:-itsm-search}"

: "${OPENSEARCH_ADMIN_PASSWORD:?set OPENSEARCH_ADMIN_PASSWORD}"
: "${OPENSEARCH_PASSWORD:?set OPENSEARCH_PASSWORD}"

hash_password() {
  docker run --rm "${IMAGE}" \
    bash -c "plugins/opensearch-security/tools/hash.sh -p '$1'" 2>/dev/null | tail -1 | tr -d '\r'
}

mkdir -p "${OUT_DIR}"
ADMIN_HASH="$(hash_password "${OPENSEARCH_ADMIN_PASSWORD}")"
SEARCH_HASH="$(hash_password "${OPENSEARCH_PASSWORD}")"

cat > "${OUT_DIR}/internal_users.yml" <<YML
_meta:
  type: "internalusers"
  config_version: 2

admin:
  hash: "${ADMIN_HASH}"
  reserved: true
  backend_roles:
    - "admin"
  description: "Cluster administrator"

${SEARCH_USER}:
  hash: "${SEARCH_HASH}"
  reserved: false
  backend_roles:
    - "itsm_backend"
  description: "Vox ITSM backend index and search access"
YML

chmod 600 "${OUT_DIR}/internal_users.yml"

# Least privilege for the backend: the ITSM indices, nothing else in the cluster.
cat > "${OUT_DIR}/roles.yml" <<YML
_meta:
  type: "roles"
  config_version: 2

itsm_backend:
  reserved: false
  cluster_permissions:
    - "cluster_composite_ops"
    - "cluster:monitor/health"
  index_permissions:
    - index_patterns:
        - "itsm*"
      allowed_actions:
        - "crud"
        - "create_index"
        - "indices:admin/mapping/put"
        - "indices:admin/get"
        - "indices:admin/exists"
        - "indices:admin/refresh*"
YML

cat > "${OUT_DIR}/roles_mapping.yml" <<YML
_meta:
  type: "rolesmapping"
  config_version: 2

all_access:
  reserved: true
  backend_roles:
    - "admin"

itsm_backend:
  reserved: false
  backend_roles:
    - "itsm_backend"
YML

echo "internal users: ${OUT_DIR}/internal_users.yml (admin, ${SEARCH_USER})"
echo "roles:          ${OUT_DIR}/roles.yml, ${OUT_DIR}/roles_mapping.yml"
