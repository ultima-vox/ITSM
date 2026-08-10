-- Problem Management stores opaque Service Desk IDs and validates through public contract.
-- Cross-module FK blocks future extraction and exposes Service Desk physical ownership.
ALTER TABLE problem_work_item
  DROP CONSTRAINT IF EXISTS problem_work_item_work_item_id_fkey;

ALTER TABLE problem_incident
  DROP CONSTRAINT IF EXISTS problem_incident_work_item_id_fkey;

ALTER TABLE asset
  DROP CONSTRAINT IF EXISTS asset_configuration_item_id_fkey;

ALTER TABLE work_item_configuration_item
  DROP CONSTRAINT IF EXISTS work_item_configuration_item_configuration_item_id_fkey;

ALTER TABLE work_item_attachment
  DROP CONSTRAINT IF EXISTS work_item_attachment_attachment_id_fkey;

ALTER TABLE catalog_item
  DROP CONSTRAINT IF EXISTS catalog_item_form_definition_id_fkey,
  DROP CONSTRAINT IF EXISTS catalog_item_workflow_definition_id_fkey;
