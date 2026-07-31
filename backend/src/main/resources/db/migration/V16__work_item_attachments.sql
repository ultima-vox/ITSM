-- Link table: work items ↔ attachments (many-to-many; attachment may attach to one WI typically)
CREATE TABLE IF NOT EXISTS work_item_attachment (
    work_item_id   uuid NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    attachment_id  uuid NOT NULL REFERENCES attachment(id) ON DELETE CASCADE,
    linked_by      varchar(128) NOT NULL,
    linked_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (work_item_id, attachment_id)
);

CREATE INDEX IF NOT EXISTS work_item_attachment_attachment_idx
    ON work_item_attachment (attachment_id);

CREATE INDEX IF NOT EXISTS work_item_attachment_work_item_idx
    ON work_item_attachment (work_item_id, linked_at DESC);
