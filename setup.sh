#!/usr/bin/env bash
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════════
# vox ITSM — one-command installer
#
# Usage:
#   ./setup.sh              Full install (infra + backend + frontend)
#   ./setup.sh --infra-only Start Docker infrastructure only
#   ./setup.sh --dev        Full install + start dev servers
#   ./setup.sh --help       Show this help
# ═══════════════════════════════════════════════════════════════════════════════

BOLD='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${BOLD}$*${NC}"; }
ok()   { echo -e "${GREEN}✓${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }
err()  { echo -e "${RED}✗${NC} $*"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_ONLY=false
DEV_MODE=false

for arg in "$@"; do
  case "$arg" in
    --infra-only) INFRA_ONLY=true ;;
    --dev)        DEV_MODE=true ;;
    --help|-h)
      head -12 "$0" | tail -9
      exit 0
      ;;
    *) err "Unknown option: $arg" ;;
  esac
done

# ── 1. Prerequisites ────────────────────────────────────────────────────────

log "=== Checking prerequisites ==="

# Docker
command -v docker >/dev/null 2>&1 || err "Docker not found. Install: https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 || docker-compose version >/dev/null 2>&1 || err "Docker Compose not found."
ok "Docker $(docker --version | awk '{print $3}' | tr -d ',')"

# Java 25 (backend)
if command -v java >/dev/null 2>&1; then
  JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d. -f1)
  if [ "$JAVA_VER" -ge 25 ] 2>/dev/null; then
    ok "Java $JAVA_VER"
  else
    warn "Java $JAVA_VER found but Java 25+ required for backend compilation"
    warn "Backend will not build locally — Docker images will build instead"
  fi
else
  warn "Java not found — backend will build inside Docker"
fi

# Node.js 22+ (frontend)
if command -v node >/dev/null 2>&1; then
  NODE_VER=$(node -v | tr -d 'v' | cut -d. -f1)
  if [ "$NODE_VER" -ge 22 ] 2>/dev/null; then
    ok "Node.js $(node -v)"
  else
    err "Node.js $NODE_VER found but 22+ required. Install: https://nodejs.org/"
  fi
else
  err "Node.js not found. Install: https://nodejs.org/"
fi

# npm
command -v npm >/dev/null 2>&1 || err "npm not found."
ok "npm $(npm -v)"

# ── 2. Docker Infrastructure ────────────────────────────────────────────────

log "=== Starting Docker infrastructure ==="

cd "$SCRIPT_DIR"
docker compose up -d

log "=== Waiting for services ==="

# PostgreSQL
echo -n "  PostgreSQL  "
for i in $(seq 1 30); do
  docker compose exec -T postgres pg_isready -U itsm -d itsm >/dev/null 2>&1 && break
  sleep 2
done
docker compose exec -T postgres pg_isready -U itsm -d itsm >/dev/null 2>&1 && ok "ready" || warn "timeout"

# Redis
echo -n "  Redis       "
for i in $(seq 1 15); do
  docker compose exec -T redis redis-cli ping >/dev/null 2>&1 && break
  sleep 2
done
docker compose exec -T redis redis-cli ping >/dev/null 2>&1 && ok "ready" || warn "timeout"

# RabbitMQ
echo -n "  RabbitMQ    "
for i in $(seq 1 20); do
  docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1 && break
  sleep 3
done
docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1 && ok "ready" || warn "timeout"

# OpenSearch
echo -n "  OpenSearch  "
for i in $(seq 1 20); do
  curl -sf http://localhost:9200/_cluster/health >/dev/null 2>&1 && break
  sleep 3
done
curl -sf http://localhost:9200/_cluster/health >/dev/null 2>&1 && ok "ready" || warn "timeout"

# MinIO
echo -n "  MinIO       "
for i in $(seq 1 15); do
  curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1 && break
  sleep 2
done
curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1 && ok "ready" || warn "timeout"

# Keycloak
echo -n "  Keycloak    "
for i in $(seq 1 30); do
  curl -sf http://localhost:8081/realms/itsm/.well-known/openid-configuration >/dev/null 2>&1 && break
  sleep 3
done
curl -sf http://localhost:8081/realms/itsm/.well-known/openid-configuration >/dev/null 2>&1 && ok "ready" || warn "timeout"

if [ "$INFRA_ONLY" = true ]; then
  log "=== Infrastructure started (infra-only mode) ==="
  echo ""
  echo "Services:"
  echo "  PostgreSQL    localhost:5432   (itsm/itsm/itsm)"
  echo "  Redis         localhost:6379"
  echo "  RabbitMQ      localhost:5672   (guest/guest)"
  echo "  OpenSearch    localhost:9200"
  echo "  MinIO         localhost:9000   (minioadmin/minioadmin)"
  echo "  MinIO Console localhost:9001"
  echo "  Keycloak      localhost:8081   (admin/admin)"
  exit 0
fi

# ── 3. Backend Build ────────────────────────────────────────────────────────

log "=== Building backend ==="

cd "$SCRIPT_DIR/backend"

if command -v java >/dev/null 2>&1 && [ "$JAVA_VER" -ge 25 ] 2>/dev/null; then
  ./gradlew classes -q 2>/dev/null && ok "Backend compiled" || warn "Backend compile skipped (Java version mismatch)"
else
  warn "Skipping local backend build (Java 25+ required)"
  echo "  Build with Docker: docker compose -f docker-compose.prod.yml build backend"
fi

cd "$SCRIPT_DIR"

# ── 4. Frontend Build ──────────────────────────────────────────────────────

log "=== Installing frontend dependencies ==="

cd "$SCRIPT_DIR/frontend"
npm ci --ignore-scripts 2>/dev/null && ok "Dependencies installed" || npm install --ignore-scripts 2>/dev/null && ok "Dependencies installed" || warn "npm install had issues"

log "=== Building frontend ==="
npm run build 2>/dev/null && ok "Frontend built" || warn "Frontend build had warnings"

cd "$SCRIPT_DIR"

# ── 5. Summary ──────────────────────────────────────────────────────────────

log "=== vox ITSM ready ==="
echo ""
echo "Infrastructure:"
echo "  PostgreSQL    localhost:5432   (itsm/itsm/itsm)"
echo "  Redis         localhost:6379"
echo "  RabbitMQ      localhost:5672   (guest/guest)"
echo "  RabbitMQ UI   localhost:15672"
echo "  OpenSearch    localhost:9200"
echo "  MinIO         localhost:9000   (minioadmin/minioadmin)"
echo "  MinIO Console localhost:9001"
echo "  Keycloak      localhost:8081   (admin/admin, realm: itsm)"
echo ""
echo "Start backend:"
echo "  cd backend"
echo "  export SPRING_PROFILES_ACTIVE=dev,compose"
echo "  export DATABASE_URL=jdbc:postgresql://localhost:5432/itsm"
echo "  export DATABASE_USER=itsm"
echo "  export DATABASE_PASSWORD=itsm"
echo "  ./gradlew bootRun"
echo ""
echo "Start frontend:"
echo "  cd frontend"
echo "  cp .env.example .env"
echo "  npm run dev"
echo ""
echo "Open: http://localhost:5173"
echo ""

if [ "$DEV_MODE" = true ]; then
  log "=== Starting dev servers ==="
  cd "$SCRIPT_DIR/backend"
  ./gradlew bootRun &
  BACKEND_PID=$!
  cd "$SCRIPT_DIR/frontend"
  npm run dev &
  FRONTEND_PID=$!
  echo "Backend PID: $BACKEND_PID"
  echo "Frontend PID: $FRONTEND_PID"
  echo "Press Ctrl+C to stop"
  wait
fi
