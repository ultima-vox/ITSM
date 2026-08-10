param(
  [Parameter(Mandatory = $true)][string]$Backup,
  [string]$Container = "itsm-postgres-1",
  [string]$User = "itsm"
)

$ErrorActionPreference = "Stop"
$source = (Resolve-Path -LiteralPath $Backup).Path
$verifyDb = "itsm_verify_" + [Guid]::NewGuid().ToString("N")
$containerPath = "/tmp/verify.dump"
$checksumPath = "$source.sha256"

if (Test-Path -LiteralPath $checksumPath) {
  $expected = ((Get-Content -Raw -LiteralPath $checksumPath).Trim() -split '\s+')[0]
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash.ToLowerInvariant()
  if ($actual -ne $expected.ToLowerInvariant()) { throw "backup checksum mismatch" }
}

try {
  docker cp $source "${Container}:${containerPath}"
  if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }
  docker exec $Container createdb --username=$User $verifyDb
  if ($LASTEXITCODE -ne 0) { throw "createdb failed" }
  docker exec $Container pg_restore --username=$User --dbname=$verifyDb --exit-on-error $containerPath
  if ($LASTEXITCODE -ne 0) { throw "pg_restore failed" }
  $tables = docker exec $Container psql --username=$User --dbname=$verifyDb --tuples-only --no-align --command="select count(*) from information_schema.tables where table_schema='public';"
  if ($LASTEXITCODE -ne 0 -or [int]$tables -lt 1) { throw "restored database has no public tables" }
  Write-Output "verified database=$verifyDb tables=$tables"
} finally {
  docker exec $Container dropdb --username=$User --if-exists $verifyDb 2>$null | Out-Null
  docker exec $Container rm -f $containerPath 2>$null | Out-Null
}
