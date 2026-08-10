#Requires -Version 5.1
[CmdletBinding()]
param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$failed = 0
$passed = 0

function Write-Ok([string]$Message) {
  Write-Host "  OK  $Message" -ForegroundColor Green
}

function Write-Fail([string]$Message) {
  Write-Host "  FAIL $Message" -ForegroundColor Red
}

function Invoke-Check {
  param(
    [string]$Name,
    [string]$Url,
    [hashtable]$Headers = @{},
    [int[]]$AcceptStatus = @(200)
  )
  try {
    $response = Invoke-WebRequest -Uri $Url -Method GET -Headers $Headers -UseBasicParsing -TimeoutSec 15
    $code = [int]$response.StatusCode
    if ($AcceptStatus -contains $code) {
      Write-Ok "$Name ($code) $Url"
      $script:passed++
      return
    }
    Write-Fail "$Name unexpected status $code (expected $($AcceptStatus -join ',')) $Url"
  }
  catch {
    $code = $null
    if ($_.Exception.Response) {
      $code = [int]$_.Exception.Response.StatusCode
    }
    if ($null -ne $code -and $AcceptStatus -contains $code) {
      Write-Ok "$Name ($code) $Url"
      $script:passed++
      return
    }
    Write-Fail "$Name $Url - $($_.Exception.Message)"
  }
  $script:failed++
}

Write-Host ""
Write-Host "VOX ITSM API smoke - $BaseUrl" -ForegroundColor Cyan
Write-Host ("-" * 48)

Invoke-Check -Name "actuator health" -Url "$BaseUrl/actuator/health"
Invoke-Check -Name "swagger-ui" -Url "$BaseUrl/swagger-ui.html"
Invoke-Check -Name "openapi docs" -Url "$BaseUrl/v3/api-docs"

if (-not [string]::IsNullOrWhiteSpace($Token)) {
  Invoke-Check `
    -Name "work-items list" `
    -Url "$BaseUrl/api/v1/work-items" `
    -Headers @{ Authorization = "Bearer $Token" }
}
else {
  Write-Host "  SKIP work-items list (no -Token)" -ForegroundColor DarkYellow
}

Write-Host ("-" * 48)
if ($failed -eq 0) {
  Write-Host "SMOKE PASSED ($passed checks)" -ForegroundColor Green
  exit 0
}

Write-Host "SMOKE FAILED ($failed failed, $passed passed)" -ForegroundColor Red
exit 1
