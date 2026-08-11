ALTER TABLE object_definition ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE object_definition DROP CONSTRAINT object_definition_object_key_version_key;
ALTER TABLE object_definition ADD CONSTRAINT object_definition_org_key_version_unique
    UNIQUE (org_id, object_key, version);

ALTER TABLE form_definition ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE form_definition DROP CONSTRAINT form_definition_form_key_version_key;
ALTER TABLE form_definition ADD CONSTRAINT form_definition_org_key_version_unique
    UNIQUE (org_id, form_key, version);

ALTER TABLE workflow_definition ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE workflow_definition DROP CONSTRAINT workflow_definition_object_key_version_key;
ALTER TABLE workflow_definition ADD CONSTRAINT workflow_definition_org_key_version_unique
    UNIQUE (org_id, object_key, version);

ALTER TABLE automation_rule ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE automation_rule DROP CONSTRAINT automation_rule_rule_key_key;
ALTER TABLE automation_rule ADD CONSTRAINT automation_rule_org_key_unique UNIQUE (org_id, rule_key);

ALTER TABLE automation_action_log ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE automation_action_log DROP CONSTRAINT automation_action_log_rule_key_event_id_action_type_key;
ALTER TABLE automation_action_log ADD CONSTRAINT automation_action_log_org_rule_event_action_unique
    UNIQUE (org_id, rule_key, event_id, action_type);
