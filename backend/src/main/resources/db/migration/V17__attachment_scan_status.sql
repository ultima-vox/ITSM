-- Malware / content scan status for attachments (stub engines write CLEAN/SKIPPED)
ALTER TABLE attachment
    ADD COLUMN IF NOT EXISTS scan_status varchar(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS scan_engine varchar(64),
    ADD COLUMN IF NOT EXISTS scan_detail varchar(500),
    ADD COLUMN IF NOT EXISTS scanned_at timestamptz;

-- Existing rows: treat as scanned clean by default so downloads keep working
UPDATE attachment
SET scan_status = 'CLEAN',
    scan_engine = 'legacy-default',
    scanned_at = COALESCE(scanned_at, created_at)
WHERE scan_status = 'PENDING' AND scanned_at IS NULL;
