CREATE TABLE notification_preference (
  org_id varchar(128) NOT NULL,
  subject_id varchar(128) NOT NULL,
  email_enabled boolean NOT NULL DEFAULT true,
  desktop_enabled boolean NOT NULL DEFAULT false,
  sla_alerts_enabled boolean NOT NULL DEFAULT true,
  assignment_enabled boolean NOT NULL DEFAULT true,
  mentions_enabled boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (org_id, subject_id)
);

CREATE INDEX notification_preference_subject_idx
  ON notification_preference(subject_id, org_id);
