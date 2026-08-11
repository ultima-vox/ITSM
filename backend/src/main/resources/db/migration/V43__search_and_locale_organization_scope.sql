ALTER TABLE user_locale_preference ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE user_locale_preference DROP CONSTRAINT user_locale_preference_pkey;
ALTER TABLE user_locale_preference ADD CONSTRAINT user_locale_preference_pkey PRIMARY KEY (org_id, subject_id);

ALTER TABLE search_document ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE search_document DROP CONSTRAINT search_document_pkey;
ALTER TABLE search_document ADD CONSTRAINT search_document_pkey PRIMARY KEY (org_id, id);
CREATE INDEX search_document_org_updated_idx ON search_document (org_id, updated_at DESC);
