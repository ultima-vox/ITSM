CREATE TABLE catalog_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), item_key varchar(100) NOT NULL UNIQUE, status varchar(30) NOT NULL,
 form_definition_id uuid NOT NULL REFERENCES form_definition(id), workflow_definition_id uuid NOT NULL REFERENCES workflow_definition(id),
 eligibility_rules jsonb NOT NULL DEFAULT '[]'::jsonb, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX catalog_item_status_idx ON catalog_item(status);
CREATE TABLE catalog_item_translation (
 catalog_item_id uuid NOT NULL REFERENCES catalog_item(id), locale varchar(35) NOT NULL, name varchar(240) NOT NULL,
 description text NOT NULL, category varchar(120), PRIMARY KEY(catalog_item_id, locale)
);
