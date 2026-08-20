#!/usr/bin/env bash
# Self-signed TLS material for the production-shaped Compose stack.
# Real deployments terminate TLS with certificates from their own CA or ACME;
# this only exists so the prod profile can be rehearsed end to end.
#
#   ./scripts/gen-tls-cert.sh                       # itsm.test + id.itsm.test
#   ITSM_HOST=itsm.example ID_HOST=id.itsm.example ./scripts/gen-tls-cert.sh
set -euo pipefail

ITSM_HOST="${ITSM_HOST:-itsm.test}"
ID_HOST="${ID_HOST:-id.itsm.test}"
OUT_DIR="${OUT_DIR:-certs}"
DAYS="${DAYS:-825}"

mkdir -p "${OUT_DIR}"
cat > "${OUT_DIR}/openssl.cnf" <<CONF
[req]
distinguished_name = dn
x509_extensions    = ext
prompt             = no

[dn]
CN = ${ITSM_HOST}

[ext]
basicConstraints = critical, CA:FALSE
keyUsage         = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName   = @alt

[alt]
DNS.1 = ${ITSM_HOST}
DNS.2 = ${ID_HOST}
IP.1  = 127.0.0.1
CONF

openssl req -x509 -newkey rsa:2048 -sha256 -days "${DAYS}" -nodes \
  -keyout "${OUT_DIR}/edge.key" -out "${OUT_DIR}/edge.crt" \
  -config "${OUT_DIR}/openssl.cnf" >/dev/null 2>&1

chmod 600 "${OUT_DIR}/edge.key"
echo "certificate: ${OUT_DIR}/edge.crt  (${ITSM_HOST}, ${ID_HOST})"
echo "private key: ${OUT_DIR}/edge.key"
