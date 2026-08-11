CREATE TABLE workflow_timer (
    id                      uuid PRIMARY KEY,
    org_id                  varchar(128) NOT NULL,
    workflow_instance_id    uuid NOT NULL REFERENCES workflow_instance(id),
    object_type             varchar(100) NOT NULL,
    object_id               varchar(200) NOT NULL,
    transition_key          varchar(100) NOT NULL,
    definition_version      integer NOT NULL,
    source_instance_version integer NOT NULL,
    due_at                  timestamptz NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PROCESSING','RETRY','COMPLETED','CANCELLED','DEAD')),
    attempts                integer NOT NULL DEFAULT 0,
    max_attempts            integer NOT NULL,
    locked_until            timestamptz,
    last_error              varchar(2000),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    completed_at            timestamptz,
    UNIQUE (org_id, workflow_instance_id, transition_key, source_instance_version)
);

CREATE INDEX workflow_timer_due_idx
    ON workflow_timer(due_at, id) WHERE status IN ('PENDING','RETRY','PROCESSING');
CREATE INDEX workflow_timer_instance_idx
    ON workflow_timer(org_id, workflow_instance_id, created_at DESC);

INSERT INTO permission(permission_key, description) VALUES
  ('workflow.timer.read', 'Inspect durable workflow timers')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN','SERVICE_DESK_MANAGER','CHANGE_MANAGER')
  AND p.permission_key = 'workflow.timer.read'
ON CONFLICT DO NOTHING;
