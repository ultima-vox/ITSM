CREATE TABLE working_calendar (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id VARCHAR(128) NOT NULL,
  calendar_key VARCHAR(100) NOT NULL,
  zone_id VARCHAR(80) NOT NULL,
  working_days VARCHAR(12)[] NOT NULL,
  starts_at TIME NOT NULL,
  ends_at TIME NOT NULL,
  holidays DATE[] NOT NULL DEFAULT '{}',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT working_calendar_window CHECK (ends_at > starts_at),
  CONSTRAINT working_calendar_org_key UNIQUE (org_id, calendar_key)
);

INSERT INTO working_calendar (
  org_id, calendar_key, zone_id, working_days, starts_at, ends_at
) VALUES (
  'default', 'default-business', 'Europe/Moscow',
  ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'], '09:00', '18:00'
);
