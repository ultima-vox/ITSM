# Smoke-check Redis, OpenSearch, MinIO and (optionally) backend integration endpoints.
# Usage:
#   .\scripts\smoke-integrations.ps1
#   .\scripts\smoke-integrations.ps1 -BaseUrl http://localhost:8080 -Token $token

param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OpenSearchUrl = "http://localhost:9200",
  [string]$RedisHost = "localhost",
  [int]$RedisPort = 6379,
  [string]$MinioUrl = "http://localhost:9000/minio/health/live",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$failed = 0

function Ok($msg) { Write-Host "  OK  $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "  FAIL $msg" -ForegroundColor Red; $script:failed++ }

Write-Host "=== Integration smoke ===" -ForegroundColor Cyan

# Redis PING via redis-cli if present, else TCP connect
try {
  $redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
  if ($redisCli) {
    $pong = & redis-cli -h $RedisHost -p $RedisPort PING 2>$null
    if ($pong -match "PONG") { Ok "Redis PING $RedisHost`:$RedisPort" } else { Fail "Redis PING unexpected: $pong" }
  } else {
    $tcp = Test-NetConnection -ComputerName $RedisHost -Port $RedisPort -WarningAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) { Ok "Redis TCP $RedisHost`:$RedisPort open (install redis-cli for PING)" }
    else { Fail "Redis TCP $RedisHost`:$RedisPort closed" }
  }
} catch {
  Fail "Redis check: $_"
}

# OpenSearch cluster health
try {
  $os = Invoke-RestMethod -Uri "$OpenSearchUrl/_cluster/health" -TimeoutSec 8
  if ($os.status -in @("green", "yellow")) {
    Ok "OpenSearch cluster status=$($os.status) nodes=$($os.number_of_nodes)"
  } else {
    Fail "OpenSearch unhealthy status=$($os.status)"
  }
} catch {
  Fail "OpenSearch $OpenSearchUrl : $_"
}

# MinIO live
try {
  $r = Invoke-WebRequest -Uri $MinioUrl -UseBasicParsing -TimeoutSec 5
  if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { Ok "MinIO health $MinioUrl" }
  else { Fail "MinIO status $($r.StatusCode)" }
} catch {
  Fail "MinIO: $_"
}

# Backend actuator + integrations (optional if app running)
try {
  $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
  Ok "Backend health status=$($health.status)"
  if ($health.components) {
    foreach ($name in @("redisCache", "opensearch", "db", "rabbit")) {
      $c = $health.components.$name
      if ($null -ne $c) {
        Ok "  actuator.$name = $($c.status)"
      }
    }
  }
} catch {
  Write-Host "  SKIP backend actuator ($BaseUrl not up or unreachable)" -ForegroundColor Yellow
}

if ($Token -ne "") {
  try {
    $headers = @{ Authorization = "Bearer $Token" }
    $integ = Invoke-RestMethod -Uri "$BaseUrl/api/v1/platform/integrations" -Headers $headers -TimeoutSec 8
    Ok "integrations redis.enabled=$($integ.redis.enabled) health=$($integ.redis.health.status)"
    Ok "integrations opensearch.enabled=$($integ.opensearch.enabled) health=$($integ.opensearch.health.status)"
    Ok "integrations storage.type=$($integ.storage.type)"
  } catch {
    Fail "GET /api/v1/platform/integrations: $_"
  }
} else {
  Write-Host "  SKIP /api/v1/platform/integrations (pass -Token for JWT or use profile dev + empty token with curl)" -ForegroundColor Yellow
}

Write-Host ""
if ($failed -gt 0) {
  Write-Host "INTEGRATIONS SMOKE FAILED ($failed)" -ForegroundColor Red
  exit 1
}
Write-Host "INTEGRATIONS SMOKE PASSED" -ForegroundColor Green
exit 0
