ALTER TABLE notification ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
DROP INDEX IF EXISTS notification_recipient_created_idx;
DROP INDEX IF EXISTS notification_recipient_unread_idx;
DROP INDEX IF EXISTS notification_dedupe_uidx;
CREATE INDEX notification_org_recipient_created_idx
    ON notification (org_id, recipient_subject, created_at DESC);
CREATE INDEX notification_org_recipient_unread_idx
    ON notification (org_id, recipient_subject, created_at DESC)
    WHERE read_at IS NULL;
CREATE UNIQUE INDEX notification_org_dedupe_uidx
    ON notification (org_id, recipient_subject, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

ALTER TABLE sla_policy ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE sla_policy DROP CONSTRAINT sla_policy_policy_key_key;
ALTER TABLE sla_policy ADD CONSTRAINT sla_policy_org_key_unique UNIQUE (org_id, policy_key);

ALTER TABLE sla_clock ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
DROP INDEX IF EXISTS sla_clock_due_idx;
CREATE INDEX sla_clock_org_due_idx ON sla_clock(org_id, state, due_at)
    WHERE state IN ('RUNNING', 'PAUSED');
