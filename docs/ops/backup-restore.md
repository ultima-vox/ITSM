# PostgreSQL backup and restore verification

Create encrypted-at-rest storage outside repository, then run:

```powershell
$backup = ./scripts/backup-db.ps1
./scripts/verify-db-backup.ps1 -Backup $backup
```

Backup command creates PostgreSQL custom-format dump plus SHA-256 sidecar under ignored
`backups/`. Verification restores into randomly named isolated database, checks public
schema, then removes verification database. Failed verification makes backup unusable.

Production schedule must copy dump and checksum to immutable off-site storage. Encrypt
with organization KMS, set retention policy, monitor job failure, and run restore drill
at least quarterly. Never restore over live `itsm`; create replacement database, verify
Flyway history and record counts, stop writers, then switch connection during approved
maintenance window.
