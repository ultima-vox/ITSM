ALTER TABLE configuration_item
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX configuration_item_org_name_idx ON configuration_item (org_id, name);

ALTER TABLE ci_relationship
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX ci_relationship_org_source_idx ON ci_relationship (org_id, source_ci_id);
CREATE INDEX ci_relationship_org_target_idx ON ci_relationship (org_id, target_ci_id);

ALTER TABLE asset
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE asset DROP CONSTRAINT asset_asset_tag_key;
ALTER TABLE asset ADD CONSTRAINT asset_org_tag_key UNIQUE (org_id, asset_tag);
CREATE INDEX asset_org_status_idx ON asset (org_id, status, asset_tag);
