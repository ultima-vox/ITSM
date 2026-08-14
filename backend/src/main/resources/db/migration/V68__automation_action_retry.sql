ALTER TABLE automation_action_log ADD COLUMN attempts integer NOT NULL DEFAULT 1;
ALTER TABLE automation_action_log ADD COLUMN quarantined_at timestamptz;

CREATE TABLE automation_action_retry (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id           varchar(128) NOT NULL,
    rule_key         varchar(100) NOT NULL,
    event_id         uuid NOT NULL,
    action_type      varchar(80)  NOT NULL,
    attempts         integer NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz NOT NULL,
    quarantined_at   timestamptz,
    last_error       varchar(1000),
    event_json       jsonb NOT NULL,
    action_parameters jsonb NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, rule_key, event_id, action_type)
);

CREATE INDEX automation_action_retry_due_idx ON automation_action_retry(next_attempt_at)
    WHERE quarantined_at IS NULL;
