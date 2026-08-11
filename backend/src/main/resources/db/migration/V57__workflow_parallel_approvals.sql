CREATE TABLE workflow_approval_request (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                   varchar(128) NOT NULL,
    workflow_instance_id     uuid NOT NULL REFERENCES workflow_instance(id),
    transition_key           varchar(120) NOT NULL,
    definition_version       integer NOT NULL,
    source_instance_version  integer NOT NULL,
    attempt                  integer NOT NULL DEFAULT 1 CHECK (attempt > 0),
    mode                     varchar(20) NOT NULL CHECK (mode IN ('ANY','ALL','QUORUM')),
    quorum                   integer,
    status                   varchar(20) NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN ('PENDING','APPROVED','REJECTED','CONSUMED')),
    requested_by             varchar(255) NOT NULL,
    created_at               timestamptz NOT NULL DEFAULT now(),
    completed_at             timestamptz,
    consumed_at              timestamptz,
    UNIQUE (org_id, workflow_instance_id, transition_key, source_instance_version, attempt),
    CONSTRAINT workflow_approval_quorum_ck CHECK (
      (mode='QUORUM' AND quorum IS NOT NULL AND quorum > 0) OR
      (mode<>'QUORUM' AND quorum IS NULL)
    )
);

CREATE TABLE workflow_approval_vote (
    request_id    uuid NOT NULL REFERENCES workflow_approval_request(id) ON DELETE CASCADE,
    voter_id      varchar(255) NOT NULL,
    voter_role    varchar(100) NOT NULL,
    decision      varchar(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
    comment       varchar(4000),
    decided_at    timestamptz,
    PRIMARY KEY (request_id, voter_id)
);

CREATE INDEX workflow_approval_instance_idx
    ON workflow_approval_request(org_id, workflow_instance_id, created_at DESC);

INSERT INTO permission(permission_key, description) VALUES
  ('workflow.approval.request', 'Request workflow transition approval'),
  ('workflow.approve', 'Cast assigned workflow approval votes')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r CROSS JOIN permission p
WHERE (r.role_key IN ('ADMIN','SERVICE_DESK_MANAGER','CHANGE_MANAGER')
       AND p.permission_key IN ('workflow.approval.request','workflow.approve'))
   OR (r.role_key='SERVICE_DESK_AGENT' AND p.permission_key='workflow.approval.request')
ON CONFLICT DO NOTHING;
