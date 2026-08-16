CREATE TABLE IF NOT EXISTS work_item_watcher (
    work_item_id  uuid         NOT NULL REFERENCES work_item (id) ON DELETE CASCADE,
    subject_id    varchar(128) NOT NULL,
    watched_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (work_item_id, subject_id)
);

CREATE INDEX IF NOT EXISTS work_item_watcher_subject_idx
    ON work_item_watcher (subject_id, watched_at DESC);

COMMENT ON TABLE work_item_watcher IS 'Subjects watching a work item for notifications and UI follow';
