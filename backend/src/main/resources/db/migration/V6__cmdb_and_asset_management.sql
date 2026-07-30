CREATE TABLE configuration_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name varchar(240) NOT NULL, class_key varchar(100) NOT NULL,
 status varchar(30) NOT NULL, attributes jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX configuration_item_class_status_idx ON configuration_item(class_key, status);
CREATE TABLE ci_relationship (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), source_ci_id uuid NOT NULL REFERENCES configuration_item(id), target_ci_id uuid NOT NULL REFERENCES configuration_item(id), relationship_type varchar(50) NOT NULL, UNIQUE(source_ci_id, target_ci_id, relationship_type), CHECK(source_ci_id <> target_ci_id));
CREATE INDEX ci_relationship_source_idx ON ci_relationship(source_ci_id);
CREATE INDEX ci_relationship_target_idx ON ci_relationship(target_ci_id);
CREATE TABLE asset (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), asset_tag varchar(80) NOT NULL UNIQUE, kind varchar(40) NOT NULL, status varchar(30) NOT NULL,
 owner_subject varchar(128), configuration_item_id uuid REFERENCES configuration_item(id), acquired_on date, warranty_until date,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX asset_owner_status_idx ON asset(owner_subject, status);
CREATE TABLE asset_lifecycle_history (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), asset_id uuid NOT NULL REFERENCES asset(id), occurred_at timestamptz NOT NULL DEFAULT now(), actor_id varchar(128) NOT NULL, from_status varchar(30), to_status varchar(30) NOT NULL, owner_subject varchar(128), details jsonb NOT NULL DEFAULT '{}'::jsonb);
