#Requires -Version 5.1
<#
.SYNOPSIS
  Lightweight API smoke checks for VOX ITSM (Windows-first).

.DESCRIPTION
  Verifies actuator health, Swagger UI, and optionally lists work-items with a Bearer token.
  Exits non-zero on any failure.

.PARAMETER BaseUrl
  Backend base URL (default: http://localhost:8080)

.PARAMETER Token
  Optional OIDC access token. When set, GET /api/v1/work-items is called with Authorization: Bearer.

.EXAMPLE
  .\scripts\smoke-api.ps1
  .\scripts\smoke-api.ps1 -BaseUrl http://localhost:8080 -Token $env:ITSM_TOKEN
#>
[CmdletBinding()]
param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$failed = 0
$passed = 0

function Write-Ok([string]$msg) {
  Write-Host "  OK  $msg" -ForegroundColor Green
}

function Write-Fail([string]$msg) {
  Write-Host "  FAIL $msg" -ForegroundColor Red
}

function Invoke-Check {
  param(
    [string]$Name,
    [string]$Url,
    [hashtable]$Headers = @{},
    [int[]]$AcceptStatus = @(200)
  )
  try {
    $resp = Invoke-WebRequest -Uri $Url -Method GET -Headers $Headers -UseBasicParsing -TimeoutSec 15
    $code = [int]$resp.StatusCode
    if ($AcceptStatus -contains $code) {
      Write-Ok "$Name ($code) $Url"
      $script:passed++
      return $true
    }
    Write-Fail "$Name unexpected status $code (expected $($AcceptStatus -join ',')) $Url"
    $script:failed++
    return $false
  }
  catch {
    $code = $null
    if ($_.Exception.Response) {
      $code = [int]$_.Exception.Response.StatusCode
      if ($AcceptStatus -contains $code) {
        Write-Ok "$Name ($code) $Url"
        $script:passed++
        return $true
      }
      Write-Fail "$Name status $code $Url — $($_.Exception.Message)"
    }
    else {
      Write-Fail "$Name $Url — $($_.Exception.Message)"
    }
    $script:failed++
    return $false
  }
}

Write-Host ""
Write-Host "VOX ITSM API smoke — $BaseUrl" -ForegroundColor Cyan
Write-Host ("-" * 48)

# 1. Health (public)
Invoke-Check -Name "actuator health" -Url "$BaseUrl/actuator/health" | Out-Null

# 2. Swagger UI (public; may 200 or 302 redirect to index)
try {
  $sw = Invoke-WebRequest -Uri "$BaseUrl/swagger-ui.html" -Method GET -UseBasicParsing -TimeoutSec 15 -MaximumRedirection 5
  $code = [int]$sw.StatusCode
  if ($code -ge 200 -and $code -lt 400) {
    Write-Ok "swagger-ui ($code) $BaseUrl/swagger-ui.html"
    $passed++
  }
  else {
    Write-Fail "swagger-ui unexpected status $code $BaseUrl/swagger-ui.html"
    $failed++
  }
}
catch {
  Write-Fail "swagger-ui $BaseUrl/swagger-ui.html — $($_.Exception.Message)"
  $failed++
}

# OpenAPI docs JSON (public per README)
Invoke-Check -Name "openapi docs" -Url "$BaseUrl/v3/api-docs" | Out-Null

# 3. Optional work-items with Bearer token
if (-not [string]::IsNullOrWhiteSpace($Token)) {
  $auth = @{ Authorization = "Bearer $Token" }
  Invoke-Check -Name "work-items list" -Url "$BaseUrl/api/v1/work-items" -Headers $auth | Out-Null
}
else {
  Write-Host "  SKIP work-items list (no -Token)" -ForegroundColor DarkYellow
}

Write-Host ("-" * 48)
if ($failed -eq 0) {
  Write-Host "SMOKE PASSED  ($passed checks)" -ForegroundColor Green
  Write-Host ""
  exit 0
}

Write-Host "SMOKE FAILED  ($failed failed, $passed passed)" -ForegroundColor Red
Write-Host ""
exit 1
