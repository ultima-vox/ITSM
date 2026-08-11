CREATE TABLE major_incident (
 id UUID PRIMARY KEY, org_id VARCHAR(128) NOT NULL, work_item_id UUID NOT NULL REFERENCES work_item(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('DECLARED','RESOLVED')),
 commander_id VARCHAR(128) NOT NULL, summary VARCHAR(2000) NOT NULL,
 declared_at TIMESTAMPTZ NOT NULL, resolved_at TIMESTAMPTZ,
 UNIQUE(org_id, work_item_id)
);
CREATE INDEX major_incident_org_status_idx ON major_incident(org_id,status,declared_at DESC);
INSERT INTO permission(permission_key,description) VALUES('work-item.major','Declare and resolve major incidents') ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.role_key IN('ADMIN','SERVICE_DESK_MANAGER') AND p.permission_key='work-item.major' ON CONFLICT DO NOTHING;
