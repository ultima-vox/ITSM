-- Permissions used by business module AccessControl checks (extends V10 seed)
INSERT INTO permission (permission_key, description) VALUES
    ('catalog.read', 'List and view service catalog items'),
    ('catalog.request', 'Submit catalog service requests'),
    ('cmdb.read', 'Read configuration items and impact'),
    ('asset.read', 'Read assets'),
    ('asset.write', 'Mutate assets'),
    ('knowledge.read', 'Read published knowledge articles'),
    ('knowledge.vote', 'Vote on knowledge article helpfulness'),
    ('problem.read', 'Read problems'),
    ('problem.write', 'Create and update problems'),
    ('change.write', 'Create and transition changes'),
    ('ai.summarize', 'Use AI copilot summarize'),
    ('ai.suggest', 'Use AI copilot resolution suggestions'),
    ('work-item.comment', 'Add comments on work items')
ON CONFLICT (permission_key) DO NOTHING;

-- Grant new business permissions to ADMIN
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key IN (
    'catalog.read', 'catalog.request', 'cmdb.read', 'asset.read', 'asset.write',
    'knowledge.read', 'knowledge.vote', 'problem.read', 'problem.write',
    'change.write', 'ai.summarize', 'ai.suggest', 'work-item.comment'
  )
ON CONFLICT DO NOTHING;

-- Service desk operators need comment permission (missing from V10 seed)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key IN ('SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
  AND p.permission_key = 'work-item.comment'
ON CONFLICT DO NOTHING;

-- CHANGE_MANAGER: change + problem + cmdb read
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'CHANGE_MANAGER'
  AND p.permission_key IN ('change.read', 'change.write', 'change.approve', 'change.manage', 'problem.read', 'problem.write', 'cmdb.read')
ON CONFLICT DO NOTHING;

-- REQUESTER: catalog + knowledge
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'REQUESTER'
  AND p.permission_key IN ('catalog.read', 'catalog.request', 'knowledge.read', 'knowledge.vote')
ON CONFLICT DO NOTHING;

-- Sequences for human-readable numbers
CREATE SEQUENCE IF NOT EXISTS problem_number_seq START WITH 1000;
CREATE SEQUENCE IF NOT EXISTS change_number_seq START WITH 1000;
CREATE SEQUENCE IF NOT EXISTS knowledge_number_seq START WITH 1000;
CREATE SEQUENCE IF NOT EXISTS catalog_request_number_seq START WITH 1000;

-- Knowledge: public slug for stable URLs
ALTER TABLE knowledge_article ADD COLUMN IF NOT EXISTS slug varchar(200);
CREATE UNIQUE INDEX IF NOT EXISTS knowledge_article_slug_uidx ON knowledge_article (slug) WHERE slug IS NOT NULL;

-- Change: CAB / risk advisory fields
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS business_justification text;
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS cab_notes text;
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS cab_risk_level varchar(30);

-- Problem ↔ work item links (explicit module table; V5 problem_incident remains for legacy)
CREATE TABLE IF NOT EXISTS problem_work_item (
  problem_id uuid NOT NULL REFERENCES problem (id),
  work_item_id uuid NOT NULL REFERENCES work_item (id),
  linked_at timestamptz NOT NULL DEFAULT now(),
  linked_by varchar(128) NOT NULL,
  PRIMARY KEY (problem_id, work_item_id)
);
CREATE INDEX IF NOT EXISTS problem_work_item_work_item_idx ON problem_work_item (work_item_id);

-- Catalog request drafts / submissions (fulfillment may later create service-desk work items)
CREATE TABLE IF NOT EXISTS catalog_request (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  number varchar(32) NOT NULL UNIQUE,
  catalog_item_id uuid NOT NULL REFERENCES catalog_item (id),
  requester_id varchar(128) NOT NULL,
  status varchar(30) NOT NULL,
  form_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS catalog_request_item_idx ON catalog_request (catalog_item_id, created_at DESC);
CREATE INDEX IF NOT EXISTS catalog_request_requester_idx ON catalog_request (requester_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Demo seed: form + workflow for catalog items
-- ---------------------------------------------------------------------------
INSERT INTO form_definition (id, form_key, object_key, version, active, definition)
VALUES (
  'a1000000-0000-4000-8000-000000000001',
  'catalog.generic-request',
  'catalog-request',
  1,
  true,
  '{"fields":[{"key":"justification","type":"textarea","required":true},{"key":"urgency","type":"select","options":["LOW","MEDIUM","HIGH"]}]}'::jsonb
) ON CONFLICT DO NOTHING;

INSERT INTO workflow_definition (id, object_key, version, active, definition)
VALUES (
  'a1000000-0000-4000-8000-000000000002',
  'catalog-request',
  1,
  true,
  '{"states":["SUBMITTED","FULFILLING","CLOSED"],"initial":"SUBMITTED"}'::jsonb
) ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Demo seed: service catalog items
-- ---------------------------------------------------------------------------
INSERT INTO catalog_item (id, item_key, status, form_definition_id, workflow_definition_id, eligibility_rules)
VALUES
  (
    'b1000000-0000-4000-8000-000000000001',
    'vpn-access',
    'PUBLISHED',
    'a1000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000002',
    '[]'::jsonb
  ),
  (
    'b1000000-0000-4000-8000-000000000002',
    'laptop-request',
    'PUBLISHED',
    'a1000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000002',
    '[]'::jsonb
  ),
  (
    'b1000000-0000-4000-8000-000000000003',
    'software-install',
    'PUBLISHED',
    'a1000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000002',
    '[]'::jsonb
  )
ON CONFLICT DO NOTHING;

INSERT INTO catalog_item_translation (catalog_item_id, locale, name, description, category)
VALUES
  ('b1000000-0000-4000-8000-000000000001', 'ru', 'Доступ к VPN', 'Запрос удалённого доступа к корпоративной сети', 'Доступы'),
  ('b1000000-0000-4000-8000-000000000001', 'en', 'VPN access', 'Request remote access to the corporate network', 'Access'),
  ('b1000000-0000-4000-8000-000000000002', 'ru', 'Запрос ноутбука', 'Выдача корпоративного ноутбука сотруднику', 'Оборудование'),
  ('b1000000-0000-4000-8000-000000000002', 'en', 'Laptop request', 'Issue a corporate laptop to an employee', 'Hardware'),
  ('b1000000-0000-4000-8000-000000000003', 'ru', 'Установка ПО', 'Установка утверждённого программного обеспечения', 'ПО'),
  ('b1000000-0000-4000-8000-000000000003', 'en', 'Software install', 'Install approved software', 'Software')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Demo seed: 4 configuration items + relationships
-- ---------------------------------------------------------------------------
INSERT INTO configuration_item (id, name, class_key, status, attributes)
VALUES
  ('c1000000-0000-4000-8000-000000000001', 'ERP Production', 'business-service', 'OPERATIONAL', '{"tier":"1","owner":"finance-ops"}'::jsonb),
  ('c1000000-0000-4000-8000-000000000002', 'app-erp-01', 'application', 'OPERATIONAL', '{"env":"prod","stack":"java"}'::jsonb),
  ('c1000000-0000-4000-8000-000000000003', 'db-erp-primary', 'database', 'OPERATIONAL', '{"engine":"postgresql","role":"primary"}'::jsonb),
  ('c1000000-0000-4000-8000-000000000004', 'host-db-01', 'server', 'OPERATIONAL', '{"datacenter":"msk-1","cpu":16}'::jsonb)
ON CONFLICT DO NOTHING;

INSERT INTO ci_relationship (id, source_ci_id, target_ci_id, relationship_type)
VALUES
  ('c2000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'DEPENDS_ON'),
  ('c2000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000003', 'DEPENDS_ON'),
  ('c2000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000004', 'HOSTED_ON'),
  ('c2000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000004', 'RUNS_ON')
ON CONFLICT DO NOTHING;

INSERT INTO asset (id, asset_tag, kind, status, owner_subject, configuration_item_id, acquired_on, warranty_until)
VALUES
  ('d1000000-0000-4000-8000-000000000001', 'AST-1001', 'SERVER', 'IN_USE', 'infra-team', 'c1000000-0000-4000-8000-000000000004', DATE '2024-03-01', DATE '2027-03-01'),
  ('d1000000-0000-4000-8000-000000000002', 'AST-2042', 'LAPTOP', 'IN_STOCK', NULL, NULL, DATE '2025-11-15', DATE '2028-11-15')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Demo seed: 3 published knowledge articles
-- ---------------------------------------------------------------------------
INSERT INTO knowledge_article (id, number, status, version, owner_subject, next_review_at, slug)
VALUES
  ('e1000000-0000-4000-8000-000000000001', 'KB-1001', 'PUBLISHED', 1, 'kb-owner', now() + interval '180 days', 'vpn-connection-guide'),
  ('e1000000-0000-4000-8000-000000000002', 'KB-1002', 'PUBLISHED', 1, 'kb-owner', now() + interval '180 days', 'reset-corporate-password'),
  ('e1000000-0000-4000-8000-000000000003', 'KB-1003', 'PUBLISHED', 1, 'kb-owner', now() + interval '180 days', 'erp-access-request')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_article_revision (id, article_id, version, locale, title, summary, body, author_subject)
VALUES
  (
    'e2000000-0000-4000-8000-000000000001',
    'e1000000-0000-4000-8000-000000000001',
    1,
    'ru',
    'Подключение к VPN',
    'Инструкция по удалённому доступу',
    '1. Установите клиент VPN. 2. Введите корпоративные учётные данные. 3. При ошибке проверьте MFA.',
    'kb-owner'
  ),
  (
    'e2000000-0000-4000-8000-000000000002',
    'e1000000-0000-4000-8000-000000000002',
    1,
    'ru',
    'Сброс корпоративного пароля',
    'Самостоятельный сброс пароля',
    'Откройте портал самообслуживания и следуйте шагам сброса. Код подтверждения придёт на корпоративную почту.',
    'kb-owner'
  ),
  (
    'e2000000-0000-4000-8000-000000000003',
    'e1000000-0000-4000-8000-000000000003',
    1,
    'ru',
    'Запрос доступа к ERP',
    'Как оформить доступ к ERP',
    'Оформите заявку в каталоге услуг «Доступ к VPN» не требуется — используйте позицию ERP access / согласуйте с руководителем.',
    'kb-owner'
  )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Demo seed: sample problem + change
-- ---------------------------------------------------------------------------
INSERT INTO problem (id, number, title, status, root_cause, workaround)
VALUES (
  'f1000000-0000-4000-8000-000000000001',
  'PRB-1000',
  'Периодические обрывы VPN в вечерние часы',
  'UNDER_INVESTIGATION',
  NULL,
  'Переподключение клиента и смена региона шлюза'
) ON CONFLICT DO NOTHING;

INSERT INTO change_request (
  id, number, type, risk, status, title,
  planned_start, planned_end, implementation_plan, rollback_plan,
  requester_id, business_justification, cab_notes, cab_risk_level
)
VALUES (
  'f2000000-0000-4000-8000-000000000001',
  'CHG-1000',
  'NORMAL',
  'MEDIUM',
  'DRAFT',
  'Обновление VPN-шлюза до 2.4',
  now() + interval '7 days',
  now() + interval '7 days 2 hours',
  'Blue-green cutover шлюза, проверка MFA и health-check',
  'Вернуть трафик на предыдущий пул шлюзов',
  'change-manager',
  'Устранение известных CVE и стабилизация вечерней нагрузки',
  NULL,
  'MEDIUM'
) ON CONFLICT DO NOTHING;
