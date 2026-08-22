-- On-call rotations and escalation policies: who answers right now, and who is woken next
-- if they do not. Rotation advances every `rotation_hours` from `rotation_start`; an override
-- row wins for its window.
CREATE TABLE on_call_schedule (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  schedule_key varchar(64) NOT NULL,
  name varchar(160) NOT NULL,
  time_zone varchar(64) NOT NULL DEFAULT 'UTC',
  rotation_hours integer NOT NULL CHECK (rotation_hours BETWEEN 1 AND 8760),
  rotation_start timestamptz NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, schedule_key)
);

CREATE TABLE on_call_participant (
  schedule_id uuid NOT NULL REFERENCES on_call_schedule(id) ON DELETE CASCADE,
  position integer NOT NULL CHECK (position >= 0),
  subject varchar(128) NOT NULL,
  PRIMARY KEY (schedule_id, position)
);

CREATE TABLE on_call_override (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  schedule_id uuid NOT NULL REFERENCES on_call_schedule(id) ON DELETE CASCADE,
  subject varchar(128) NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  reason varchar(500),
  created_by varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);
CREATE INDEX on_call_override_window_idx ON on_call_override (org_id, schedule_id, starts_at, ends_at);

CREATE TABLE escalation_policy (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  policy_key varchar(64) NOT NULL,
  name varchar(160) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_id, policy_key)
);

CREATE TABLE escalation_step (
  policy_id uuid NOT NULL REFERENCES escalation_policy(id) ON DELETE CASCADE,
  step_order integer NOT NULL CHECK (step_order >= 0),
  delay_minutes integer NOT NULL CHECK (delay_minutes >= 0 AND delay_minutes <= 10080),
  target_type varchar(16) NOT NULL CHECK (target_type IN ('SUBJECT', 'SCHEDULE')),
  target_ref varchar(128) NOT NULL,
  PRIMARY KEY (policy_id, step_order)
);

INSERT INTO permission (permission_key, description) VALUES
  ('oncall.read', 'Read on-call schedules, overrides and escalation policies'),
  ('oncall.admin', 'Create and edit on-call schedules, overrides and escalation policies')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'oncall.read'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER', 'CHANGE_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'oncall.admin'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_MANAGER')
ON CONFLICT DO NOTHING;
