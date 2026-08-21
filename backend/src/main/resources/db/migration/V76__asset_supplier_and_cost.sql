-- Asset financial identity: supplier and acquisition cost stay distinct from the linked CI.
ALTER TABLE asset ADD COLUMN IF NOT EXISTS supplier varchar(240);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS cost numeric(14, 2);
