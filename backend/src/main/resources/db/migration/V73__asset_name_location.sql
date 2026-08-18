-- Asset: add name and location fields
ALTER TABLE asset ADD COLUMN IF NOT EXISTS name varchar(240);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS location varchar(240);
