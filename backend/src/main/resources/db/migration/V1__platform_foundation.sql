CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE audit_event (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), occurred_at timestamptz NOT NULL DEFAULT now(), actor_id varchar(128) NOT NULL, action varchar(128) NOT NULL, object_type varchar(128) NOT NULL, object_id varchar(128) NOT NULL, before_state jsonb, after_state jsonb, correlation_id uuid NOT NULL, metadata jsonb NOT NULL DEFAULT '{}'::jsonb);
CREATE INDEX audit_event_object_idx ON audit_event(object_type, object_id, occurred_at DESC);
CREATE TABLE outbox_event (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), occurred_at timestamptz NOT NULL DEFAULT now(), event_type varchar(200) NOT NULL, aggregate_type varchar(100) NOT NULL, aggregate_id varchar(128) NOT NULL, payload jsonb NOT NULL, published_at timestamptz, attempts integer NOT NULL DEFAULT 0);
CREATE INDEX outbox_event_unpublished_idx ON outbox_event(occurred_at) WHERE published_at IS NULL;
