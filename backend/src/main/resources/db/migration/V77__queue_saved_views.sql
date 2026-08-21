-- Operator-owned queue filter snapshots. Built-in views stay in the client.
CREATE TABLE queue_saved_view (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id varchar(128) NOT NULL,
    owner_subject varchar(128) NOT NULL,
    name varchar(80) NOT NULL,
    tab varchar(32) NOT NULL DEFAULT 'all',
    priority varchar(16) NOT NULL DEFAULT '',
    type varchar(32) NOT NULL DEFAULT '',
    status varchar(32) NOT NULL DEFAULT '',
    sla varchar(32) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, owner_subject, name)
);
CREATE INDEX queue_saved_view_owner_idx ON queue_saved_view (org_id, owner_subject, created_at);
