-- Link work items to configuration items (CMDB impact surface)
CREATE TABLE IF NOT EXISTS work_item_configuration_item (
    work_item_id           uuid NOT NULL REFERENCES work_item (id) ON DELETE CASCADE,
    configuration_item_id  uuid NOT NULL REFERENCES configuration_item (id) ON DELETE CASCADE,
    linked_by              varchar(128) NOT NULL,
    linked_at              timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (work_item_id, configuration_item_id)
);

CREATE INDEX IF NOT EXISTS work_item_ci_ci_idx
    ON work_item_configuration_item (configuration_item_id);

COMMENT ON TABLE work_item_configuration_item IS 'Many-to-many work item ↔ CI links for impact and related records';
