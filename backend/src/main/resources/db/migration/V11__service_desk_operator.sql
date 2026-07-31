-- Operator Service Desk: full work-item fields, comments, queue indexes

ALTER TABLE work_item
  ADD COLUMN IF NOT EXISTS impact varchar(30) NOT NULL DEFAULT 'MEDIUM',
  ADD COLUMN IF NOT EXISTS urgency varchar(30) NOT NULL DEFAULT 'MEDIUM',
  ADD COLUMN IF NOT EXISTS assignee_id varchar(128),
  ADD COLUMN IF NOT EXISTS team_id varchar(128),
  ADD COLUMN IF NOT EXISTS resolution_code varchar(80),
  ADD COLUMN IF NOT EXISTS resolution_notes text,
  ADD COLUMN IF NOT EXISTS closed_at timestamptz;

CREATE TABLE IF NOT EXISTS work_item_comment (
  id uuid PRIMARY KEY,
  work_item_id uuid NOT NULL REFERENCES work_item (id) ON DELETE CASCADE,
  author_id varchar(128) NOT NULL,
  body text NOT NULL,
  created_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS work_item_comment_item_created_idx
  ON work_item_comment (work_item_id, created_at DESC);

-- Operator queue indexes
CREATE INDEX IF NOT EXISTS work_item_assignee_state_updated_idx
  ON work_item (assignee_id, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS work_item_team_state_updated_idx
  ON work_item (team_id, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS work_item_priority_state_updated_idx
  ON work_item (priority, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS work_item_type_state_updated_idx
  ON work_item (type, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS work_item_title_trgm_ready_idx
  ON work_item (lower(title));
