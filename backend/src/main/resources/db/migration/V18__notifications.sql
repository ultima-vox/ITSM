-- Persistent in-app notifications (replaces process-local demo store)
CREATE TABLE IF NOT EXISTS notification (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at         timestamptz   NOT NULL DEFAULT now(),
    correlation_id     uuid,
    template_key       varchar(200)  NOT NULL,
    recipient_subject  varchar(128)  NOT NULL,
    locale             varchar(35)   NOT NULL DEFAULT 'ru',
    variables          jsonb         NOT NULL DEFAULT '{}'::jsonb,
    channel            varchar(32)   NOT NULL,
    read_at            timestamptz,
    source             varchar(128),
    entity_type        varchar(100),
    entity_id          varchar(128),
    dedupe_key         varchar(300)
);

CREATE INDEX IF NOT EXISTS notification_recipient_created_idx
    ON notification (recipient_subject, created_at DESC);

CREATE INDEX IF NOT EXISTS notification_recipient_unread_idx
    ON notification (recipient_subject, created_at DESC)
    WHERE read_at IS NULL;

-- Prevent duplicate delivery for same recipient + logical key (e.g. assign of same item)
CREATE UNIQUE INDEX IF NOT EXISTS notification_dedupe_uidx
    ON notification (recipient_subject, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

COMMENT ON TABLE notification IS 'In-app and multi-channel notification log; PostgreSQL is source of truth';
