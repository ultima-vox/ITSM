CREATE TABLE change_request (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), number varchar(32) NOT NULL UNIQUE, type varchar(30) NOT NULL,
 risk varchar(30) NOT NULL, status varchar(30) NOT NULL, title varchar(240) NOT NULL,
 planned_start timestamptz, planned_end timestamptz, implementation_plan text NOT NULL, rollback_plan text NOT NULL,
 requester_id varchar(128) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX change_request_schedule_idx ON change_request(status, planned_start);
CREATE TABLE change_approval (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), change_id uuid NOT NULL REFERENCES change_request(id), approver_id varchar(128) NOT NULL, decision varchar(20) NOT NULL, decided_at timestamptz, comment text);
CREATE TABLE problem (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), number varchar(32) NOT NULL UNIQUE, title varchar(240) NOT NULL, status varchar(30) NOT NULL, root_cause text, workaround text, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE problem_incident (problem_id uuid NOT NULL REFERENCES problem(id), work_item_id uuid NOT NULL REFERENCES work_item(id), PRIMARY KEY(problem_id, work_item_id));
