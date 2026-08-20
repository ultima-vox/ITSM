#!/usr/bin/env bash
# Transport TLS material for OpenSearch in the production-shaped stack.
# OpenSearch refuses to enable its security plugin without transport certificates; these
# are issued by a throwaway local CA so a rehearsal does not need the demo certificates.
# Real deployments issue node certificates from their own CA.
set -euo pipefail

# Git Bash rewrites /C=US/... subjects into Windows paths unless this is set.
case "${OSTYPE:-}" in
  msys*|cygwin*) export MSYS_NO_PATHCONV=1 ;;
esac

OUT_DIR="${OUT_DIR:-certs/opensearch}"
DAYS="${DAYS:-825}"
NODE_DN="${NODE_DN:-/C=US/O=vox/OU=itsm/CN=opensearch}"
ADMIN_DN="${ADMIN_DN:-/C=US/O=vox/OU=itsm/CN=admin}"

mkdir -p "${OUT_DIR}"
cd "${OUT_DIR}"

openssl genrsa -out root-ca-key.pem 2048 2>/dev/null
openssl req -x509 -new -nodes -key root-ca-key.pem -sha256 -days "${DAYS}" \
  -subj "/C=US/O=vox/OU=itsm/CN=vox-itsm-root-ca" -out root-ca.pem 2>/dev/null

issue() {
  local name="$1" subject="$2" extra="$3"
  openssl genrsa -out "${name}-key-temp.pem" 2048 2>/dev/null
  openssl pkcs8 -inform PEM -outform PEM -in "${name}-key-temp.pem" -topk8 -nocrypt \
    -v1 PBE-SHA1-3DES -out "${name}-key.pem" 2>/dev/null
  openssl req -new -key "${name}-key.pem" -subj "${subject}" -out "${name}.csr" 2>/dev/null
  if [ -n "${extra}" ]; then
    printf '%s\n' "${extra}" > "${name}-ext.cnf"
    openssl x509 -req -in "${name}.csr" -CA root-ca.pem -CAkey root-ca-key.pem \
      -CAcreateserial -sha256 -days "${DAYS}" -extfile "${name}-ext.cnf" \
      -out "${name}.pem" 2>/dev/null
    rm -f "${name}-ext.cnf"
  else
    openssl x509 -req -in "${name}.csr" -CA root-ca.pem -CAkey root-ca-key.pem \
      -CAcreateserial -sha256 -days "${DAYS}" -out "${name}.pem" 2>/dev/null
  fi
  rm -f "${name}-key-temp.pem" "${name}.csr"
}

issue node "${NODE_DN}" "subjectAltName=DNS:opensearch,DNS:localhost,IP:127.0.0.1"
issue admin "${ADMIN_DN}" ""

chmod 644 ./*.pem
echo "transport CA:   ${OUT_DIR}/root-ca.pem"
echo "node cert:      ${OUT_DIR}/node.pem"
echo "admin cert:     ${OUT_DIR}/admin.pem"
