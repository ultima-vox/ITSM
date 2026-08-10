ALTER TABLE knowledge_article
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE knowledge_article DROP CONSTRAINT knowledge_article_number_key;
ALTER TABLE knowledge_article ADD CONSTRAINT knowledge_article_org_number_key UNIQUE (org_id, number);
DROP INDEX knowledge_article_slug_uidx;
CREATE UNIQUE INDEX knowledge_article_org_slug_uidx
  ON knowledge_article (org_id, slug) WHERE slug IS NOT NULL;
CREATE INDEX knowledge_article_org_updated_idx ON knowledge_article (org_id, updated_at DESC);

ALTER TABLE catalog_item
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE catalog_item DROP CONSTRAINT catalog_item_item_key_key;
ALTER TABLE catalog_item ADD CONSTRAINT catalog_item_org_key UNIQUE (org_id, item_key);
CREATE INDEX catalog_item_org_status_idx ON catalog_item (org_id, status);

ALTER TABLE catalog_request
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE catalog_request DROP CONSTRAINT catalog_request_number_key;
ALTER TABLE catalog_request ADD CONSTRAINT catalog_request_org_number_key UNIQUE (org_id, number);
CREATE INDEX catalog_request_org_updated_idx ON catalog_request (org_id, updated_at DESC);
