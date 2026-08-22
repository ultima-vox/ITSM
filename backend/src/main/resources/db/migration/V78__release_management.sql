-- Release and deployment management: releases group approved changes into a deployment
-- window with explicit build/test/go-no-go gates, matching the ITIL release practice.
CREATE SEQUENCE IF NOT EXISTS release_number_seq START WITH 1000;

CREATE TABLE release_record (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  number varchar(32) NOT NULL UNIQUE,
  name varchar(240) NOT NULL,
  type varchar(20) NOT NULL CHECK (type IN ('MAJOR', 'MINOR', 'PATCH', 'EMERGENCY')),
  status varchar(20) NOT NULL CHECK (status IN (
    'PLANNING', 'BUILD', 'TESTING', 'GO_NO_GO', 'DEPLOYING', 'DEPLOYED', 'ROLLED_BACK', 'CLOSED', 'CANCELLED'
  )),
  description text,
  deployment_plan text,
  rollback_plan text,
  test_summary text,
  go_decision varchar(10) CHECK (go_decision IN ('GO', 'NO_GO')),
  go_decision_notes text,
  go_decided_by varchar(128),
  go_decided_at timestamptz,
  release_manager varchar(128),
  planned_start timestamptz,
  planned_end timestamptz,
  actual_start timestamptz,
  actual_end timestamptz,
  created_by varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0
);
CREATE INDEX release_record_org_updated_idx ON release_record (org_id, updated_at DESC);
CREATE INDEX release_record_org_window_idx ON release_record (org_id, status, planned_start);

CREATE TABLE release_change (
  release_id uuid NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
  change_id uuid NOT NULL REFERENCES change_request(id),
  org_id varchar(128) NOT NULL,
  added_by varchar(128) NOT NULL,
  added_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (release_id, change_id)
);
CREATE INDEX release_change_change_idx ON release_change (org_id, change_id);

INSERT INTO permission (permission_key, description) VALUES
  ('release.read', 'Read releases and their change content'),
  ('release.write', 'Create and edit releases, link changes'),
  ('release.approve', 'Record the go / no-go decision for a release')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'release.read'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER', 'CHANGE_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN ('release.write', 'release.approve')
WHERE r.role_key IN ('ADMIN', 'CHANGE_MANAGER')
ON CONFLICT DO NOTHING;
