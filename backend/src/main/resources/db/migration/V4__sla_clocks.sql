CREATE TABLE sla_clock (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), policy_key varchar(100) NOT NULL, aggregate_id uuid NOT NULL,
 metric varchar(80) NOT NULL, started_at timestamptz NOT NULL, due_at timestamptz NOT NULL,
 warning_at timestamptz, state varchar(30) NOT NULL, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sla_clock_due_idx ON sla_clock(state, due_at) WHERE state IN ('RUNNING', 'PAUSED');
CREATE TABLE sla_clock_history (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), clock_id uuid NOT NULL REFERENCES sla_clock(id),
 occurred_at timestamptz NOT NULL DEFAULT now(), action varchar(40) NOT NULL, actor_id varchar(128), details jsonb NOT NULL DEFAULT '{}'::jsonb
);
