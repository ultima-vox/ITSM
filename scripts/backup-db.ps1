param(
  [string]$Container = "itsm-postgres-1",
  [string]$Database = "itsm",
  [string]$User = "itsm",
  [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$resolved = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$OutputDirectory"))
New-Item -ItemType Directory -Force -Path $resolved | Out-Null
$stamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$name = "$Database-$stamp.dump"
$containerPath = "/tmp/$name"
$hostPath = Join-Path $resolved $name

try {
  docker exec $Container pg_dump --username=$User --dbname=$Database --format=custom --file=$containerPath
  if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }
  docker cp "${Container}:${containerPath}" $hostPath
  if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }
} finally {
  docker exec $Container rm -f $containerPath 2>$null | Out-Null
}

$hash = (Get-FileHash -Algorithm SHA256 $hostPath).Hash.ToLowerInvariant()
Set-Content -Encoding ascii -Path "$hostPath.sha256" -Value "$hash  $name"
Write-Output $hostPath
