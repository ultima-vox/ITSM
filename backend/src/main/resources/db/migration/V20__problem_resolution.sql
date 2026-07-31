-- Permanent resolution notes required before problem RESOLVED
ALTER TABLE problem
    ADD COLUMN IF NOT EXISTS resolution text;

COMMENT ON COLUMN problem.resolution IS 'Required non-blank resolution text when transitioning to RESOLVED';
