CREATE TABLE work_item_survey (
    id UUID PRIMARY KEY,
    org_id VARCHAR(128) NOT NULL,
    work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    respondent_id VARCHAR(128) NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    submitted_at TIMESTAMPTZ NOT NULL,
    UNIQUE (org_id, work_item_id)
);

CREATE INDEX idx_work_item_survey_org_submitted
    ON work_item_survey (org_id, submitted_at DESC);
