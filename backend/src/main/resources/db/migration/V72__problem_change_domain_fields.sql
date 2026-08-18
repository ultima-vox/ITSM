-- Problem: add priority, impact, owner fields per INST.MD
ALTER TABLE problem ADD COLUMN IF NOT EXISTS priority varchar(20) DEFAULT 'MEDIUM';
ALTER TABLE problem ADD COLUMN IF NOT EXISTS impact varchar(20) DEFAULT 'MEDIUM';
ALTER TABLE problem ADD COLUMN IF NOT EXISTS owner_subject varchar(128);

-- Change: add test_plan and impact per INST.MD
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS test_plan text;
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS impact varchar(20) DEFAULT 'MEDIUM';
