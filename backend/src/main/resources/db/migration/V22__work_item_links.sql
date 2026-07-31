-- Related work items (duplicates, related, caused-by, child-of)
CREATE TABLE IF NOT EXISTS work_item_link (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       uuid         NOT NULL REFERENCES work_item (id) ON DELETE CASCADE,
    target_id       uuid         NOT NULL REFERENCES work_item (id) ON DELETE CASCADE,
    link_type       varchar(40)  NOT NULL,
    created_by      varchar(128) NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT work_item_link_no_self CHECK (source_id <> target_id),
    CONSTRAINT work_item_link_unique UNIQUE (source_id, target_id, link_type)
);

CREATE INDEX IF NOT EXISTS work_item_link_target_idx ON work_item_link (target_id);
CREATE INDEX IF NOT EXISTS work_item_link_source_idx ON work_item_link (source_id);

COMMENT ON TABLE work_item_link IS 'Directed relations between work items (RELATED, DUPLICATE_OF, CAUSED_BY, CHILD_OF)';
