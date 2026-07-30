CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE audit_event (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), occurred_at timestamptz NOT NULL DEFAULT now(), actor_id varchar(128) NOT NULL, action varchar(128) NOT NULL, object_type varchar(128) NOT NULL, object_id varchar(128) NOT NULL, before_state jsonb, after_state jsonb, correlation_id uuid NOT NULL, metadata jsonb NOT NULL DEFAULT '{}'::jsonb);
CREATE INDEX audit_event_object_idx ON audit_event(object_type, object_id, occurred_at DESC);
CREATE TABLE outbox_event (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), occurred_at timestamptz NOT NULL DEFAULT now(), event_type varchar(200) NOT NULL, aggregate_type varchar(100) NOT NULL, aggregate_id varchar(128) NOT NULL, payload jsonb NOT NULL, published_at timestamptz, attempts integer NOT NULL DEFAULT 0);
CREATE INDEX outbox_event_unpublished_idx ON outbox_event(occurred_at) WHERE published_at IS NULL;

CREATE TABLE object_definition (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), object_key varchar(100) NOT NULL, version integer NOT NULL, active boolean NOT NULL DEFAULT true, definition jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(object_key, version));
CREATE TABLE workflow_definition (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), object_key varchar(100) NOT NULL, version integer NOT NULL, active boolean NOT NULL DEFAULT true, definition jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(object_key, version));
CREATE TABLE translation (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), namespace varchar(100) NOT NULL, translation_key varchar(200) NOT NULL, locale varchar(35) NOT NULL, value text NOT NULL, version integer NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(namespace, translation_key, locale));
CREATE TABLE user_locale_preference (subject_id varchar(128) PRIMARY KEY, locale varchar(35) NOT NULL, updated_at timestamptz NOT NULL DEFAULT now());
