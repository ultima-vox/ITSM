ALTER TABLE catalog_item
    ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN approver_role VARCHAR(100),
    ADD COLUMN owner_id VARCHAR(128),
    ADD COLUMN cost_minor BIGINT,
    ADD COLUMN currency CHAR(3);

ALTER TABLE catalog_request ADD COLUMN completed_at TIMESTAMPTZ;

CREATE TABLE catalog_request_approval (
    id UUID PRIMARY KEY,
    org_id VARCHAR(128) NOT NULL,
    request_id UUID NOT NULL REFERENCES catalog_request(id) ON DELETE CASCADE,
    approver_role VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','APPROVED','REJECTED')),
    decided_by VARCHAR(128),
    decided_at TIMESTAMPTZ,
    comment VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (org_id, request_id, approver_role)
);

CREATE TABLE catalog_fulfillment_task (
    id UUID PRIMARY KEY,
    org_id VARCHAR(128) NOT NULL,
    request_id UUID NOT NULL REFERENCES catalog_request(id) ON DELETE CASCADE,
    title VARCHAR(240) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    assignee_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX catalog_approval_org_request_idx ON catalog_request_approval(org_id, request_id);
CREATE INDEX catalog_task_org_request_idx ON catalog_fulfillment_task(org_id, request_id, state);

INSERT INTO permission(permission_key, description) VALUES
    ('catalog.fulfill', 'Manage catalog fulfillment tasks'),
    ('catalog.approve', 'Approve catalog requests')
ON CONFLICT(permission_key) DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE (r.role_key='ADMIN' AND p.permission_key IN ('catalog.fulfill','catalog.approve'))
   OR (r.role_key='SERVICE_DESK_MANAGER' AND p.permission_key IN ('catalog.fulfill','catalog.approve'))
   OR (r.role_key='SERVICE_DESK_AGENT' AND p.permission_key='catalog.fulfill')
ON CONFLICT DO NOTHING;

UPDATE catalog_item SET approval_required=TRUE, approver_role='SERVICE_DESK_MANAGER',
    owner_id='workplace-team', cost_minor=15000000, currency='RUB'
WHERE item_key='laptop-request';
