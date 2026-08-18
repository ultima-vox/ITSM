CREATE TABLE user_profile (
    subject_id  varchar(128) NOT NULL,
    org_id      varchar(128) NOT NULL DEFAULT 'default',
    username    varchar(80),
    display_name varchar(240),
    email       varchar(240),
    avatar_url  varchar(512),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, subject_id)
);

CREATE INDEX idx_user_profile_org ON user_profile(org_id);

-- Seed demo Keycloak users
INSERT INTO user_profile (subject_id, org_id, username, display_name, email) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'default', 'anna',      'Anna Yakovleva',   'anna@itsm.local'),
  ('a0000000-0000-4000-8000-000000000002', 'default', 'admin',     'Platform Admin',   'admin@itsm.local'),
  ('a0000000-0000-4000-8000-000000000003', 'default', 'requester', 'Sample Requester', 'requester@itsm.local')
ON CONFLICT (org_id, subject_id) DO NOTHING;
