-- Runtime tables for platform engines (workflow instances, RBAC, automation log, search projection)

CREATE TABLE workflow_instance (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_type   varchar(100) NOT NULL,
    object_id     varchar(128) NOT NULL,
    state         varchar(80)  NOT NULL,
    definition_version integer NOT NULL,
    version       integer      NOT NULL DEFAULT 1,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (object_type, object_id)
);
CREATE INDEX workflow_instance_state_idx ON workflow_instance (object_type, state);

CREATE TABLE role (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role_key    varchar(80)  NOT NULL UNIQUE,
    labels      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    description text,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE permission (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_key  varchar(150) NOT NULL UNIQUE,
    description     text,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    role_id       uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE principal_role (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id   varchar(128) NOT NULL,
    role_id      uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    scope        jsonb NOT NULL DEFAULT '{}'::jsonb,
    granted_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (subject_id, role_id)
);
CREATE INDEX principal_role_subject_idx ON principal_role (subject_id);

CREATE TABLE automation_action_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_key     varchar(100) NOT NULL,
    event_id     uuid NOT NULL,
    action_type  varchar(80)  NOT NULL,
    status       varchar(30)  NOT NULL,
    details      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (rule_key, event_id, action_type)
);
CREATE INDEX automation_action_log_event_idx ON automation_action_log (event_id);

CREATE TABLE search_document (
    id           varchar(200) PRIMARY KEY,
    object_type  varchar(100) NOT NULL,
    title        varchar(500) NOT NULL,
    body         text         NOT NULL DEFAULT '',
    scopes       jsonb        NOT NULL DEFAULT '[]'::jsonb,
    facets       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    updated_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX search_document_type_idx ON search_document (object_type, updated_at DESC);

-- ---------------------------------------------------------------------------
-- Seed: permissions
-- ---------------------------------------------------------------------------
INSERT INTO permission (permission_key, description) VALUES
    ('work-item.read', 'Read work items'),
    ('work-item.create', 'Create work items'),
    ('work-item.update', 'Update work items'),
    ('work-item.transition', 'Transition work item workflow'),
    ('work-item.assign', 'Assign work items'),
    ('work-item.close', 'Close work items'),
    ('change.read', 'Read changes'),
    ('change.approve', 'Approve changes'),
    ('change.manage', 'Manage changes'),
    ('metadata.read', 'Read object/form/workflow metadata'),
    ('admin.full', 'Full administrative access');

-- ---------------------------------------------------------------------------
-- Seed: roles
-- ---------------------------------------------------------------------------
INSERT INTO role (role_key, labels, description) VALUES
    ('SERVICE_DESK_AGENT',   '{"en":"Service Desk Agent","ru":"Агент службы поддержки"}', 'Handles incidents and requests'),
    ('SERVICE_DESK_MANAGER', '{"en":"Service Desk Manager","ru":"Менеджер службы поддержки"}', 'Manages service desk operations'),
    ('REQUESTER',            '{"en":"Requester","ru":"Заявитель"}', 'End-user who submits requests'),
    ('ADMIN',                '{"en":"Administrator","ru":"Администратор"}', 'Full platform administration'),
    ('CHANGE_MANAGER',       '{"en":"Change Manager","ru":"Менеджер изменений"}', 'Owns change process'),
    ('CAB_MEMBER',           '{"en":"CAB Member","ru":"Участник CAB"}', 'Change advisory board member');

-- role_permission mappings
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN (
    'work-item.read','work-item.create','work-item.update','work-item.transition','work-item.assign','metadata.read'
) WHERE r.role_key = 'SERVICE_DESK_AGENT';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN (
    'work-item.read','work-item.create','work-item.update','work-item.transition','work-item.assign','work-item.close','metadata.read'
) WHERE r.role_key = 'SERVICE_DESK_MANAGER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN (
    'work-item.read','work-item.create'
) WHERE r.role_key = 'REQUESTER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN (
    'change.read','change.approve','change.manage','work-item.read','metadata.read'
) WHERE r.role_key = 'CHANGE_MANAGER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN (
    'change.read','change.approve','work-item.read'
) WHERE r.role_key = 'CAB_MEMBER';

-- ---------------------------------------------------------------------------
-- Seed: object_definition for work-item (Incident / Service Request)
-- ---------------------------------------------------------------------------
INSERT INTO object_definition (object_key, version, active, definition) VALUES (
    'work-item', 1, true,
    '{
      "labels": {"en": "Work Item", "ru": "Рабочий элемент"},
      "attributes": [
        {"key": "number",      "type": "TEXT",     "required": false, "searchable": true,  "labels": {"en": "Number", "ru": "Номер"}},
        {"key": "type",        "type": "ENUM",     "required": true,  "searchable": true,  "labels": {"en": "Type", "ru": "Тип"},
         "enumValues": ["INCIDENT", "SERVICE_REQUEST"]},
        {"key": "title",       "type": "TEXT",     "required": true,  "searchable": true,  "labels": {"en": "Title", "ru": "Заголовок"}},
        {"key": "description", "type": "RICH_TEXT","required": true,  "searchable": true,  "labels": {"en": "Description", "ru": "Описание"}},
        {"key": "service",     "type": "TEXT",     "required": true,  "searchable": true,  "labels": {"en": "Service", "ru": "Услуга"}},
        {"key": "state",       "type": "ENUM",     "required": true,  "searchable": true,  "labels": {"en": "State", "ru": "Состояние"},
         "enumValues": ["NEW","IN_PROGRESS","PENDING","RESOLVED","CLOSED","CANCELLED"]},
        {"key": "priority",    "type": "ENUM",     "required": true,  "searchable": true,  "labels": {"en": "Priority", "ru": "Приоритет"},
         "enumValues": ["CRITICAL","HIGH","MEDIUM","LOW"]},
        {"key": "requester_id","type": "USER",     "required": true,  "searchable": false, "labels": {"en": "Requester", "ru": "Заявитель"}},
        {"key": "assignee_id", "type": "USER",     "required": false, "searchable": true,  "labels": {"en": "Assignee", "ru": "Исполнитель"}},
        {"key": "created_at",  "type": "DATE_TIME","required": false, "searchable": false, "labels": {"en": "Created", "ru": "Создано"}},
        {"key": "updated_at",  "type": "DATE_TIME","required": false, "searchable": false, "labels": {"en": "Updated", "ru": "Обновлено"}}
      ],
      "relations": [
        {"key": "related_ci", "targetObjectKey": "configuration-item", "cardinality": "MANY_TO_MANY", "required": false,
         "labels": {"en": "Related CIs", "ru": "Связанные КЕ"}}
      ]
    }'::jsonb
);

-- ---------------------------------------------------------------------------
-- Seed: workflow_definition for work-item
-- ---------------------------------------------------------------------------
INSERT INTO workflow_definition (object_key, version, active, definition) VALUES (
    'work-item', 1, true,
    '{
      "initialState": "NEW",
      "states": ["NEW","IN_PROGRESS","PENDING","RESOLVED","CLOSED","CANCELLED"],
      "transitions": [
        {"key": "start",      "from": "NEW",         "to": "IN_PROGRESS", "requiredPermissions": ["work-item.transition"], "requiredFields": ["assignee_id"]},
        {"key": "hold",       "from": "IN_PROGRESS", "to": "PENDING",     "requiredPermissions": ["work-item.transition"], "requiredFields": []},
        {"key": "resume",     "from": "PENDING",     "to": "IN_PROGRESS", "requiredPermissions": ["work-item.transition"], "requiredFields": []},
        {"key": "resolve",    "from": "IN_PROGRESS", "to": "RESOLVED",    "requiredPermissions": ["work-item.transition"], "requiredFields": []},
        {"key": "reopen",     "from": "RESOLVED",    "to": "IN_PROGRESS", "requiredPermissions": ["work-item.transition"], "requiredFields": []},
        {"key": "close",      "from": "RESOLVED",    "to": "CLOSED",      "requiredPermissions": ["work-item.close"],      "requiredFields": []},
        {"key": "cancel-new", "from": "NEW",         "to": "CANCELLED",   "requiredPermissions": ["work-item.update"],     "requiredFields": []},
        {"key": "cancel-wip", "from": "IN_PROGRESS", "to": "CANCELLED",   "requiredPermissions": ["work-item.update"],     "requiredFields": []},
        {"key": "cancel-hold","from": "PENDING",     "to": "CANCELLED",   "requiredPermissions": ["work-item.update"],     "requiredFields": []}
      ]
    }'::jsonb
);

-- ---------------------------------------------------------------------------
-- Seed: form_definition for work-item
-- ---------------------------------------------------------------------------
INSERT INTO form_definition (form_key, object_key, version, active, definition) VALUES (
    'work-item.default', 'work-item', 1, true,
    '{
      "sections": [
        {
          "key": "main",
          "labels": {"en": "Details", "ru": "Основное"},
          "fields": [
            {"attributeKey": "type",        "required": true,  "visibleWhen": null, "readOnlyWhen": null},
            {"attributeKey": "title",       "required": true,  "visibleWhen": null, "readOnlyWhen": null},
            {"attributeKey": "description", "required": true,  "visibleWhen": null, "readOnlyWhen": null},
            {"attributeKey": "service",     "required": true,  "visibleWhen": null, "readOnlyWhen": null},
            {"attributeKey": "priority",    "required": true,  "visibleWhen": null, "readOnlyWhen": null}
          ]
        },
        {
          "key": "assignment",
          "labels": {"en": "Assignment", "ru": "Назначение"},
          "fields": [
            {"attributeKey": "assignee_id", "required": false, "visibleWhen": null, "readOnlyWhen": null},
            {"attributeKey": "state",       "required": false, "visibleWhen": null,
             "readOnlyWhen": {"language": "cel", "source": "true"}}
          ]
        }
      ]
    }'::jsonb
);

-- ---------------------------------------------------------------------------
-- Seed: default SLA policy
-- ---------------------------------------------------------------------------
INSERT INTO sla_policy (policy_key, enabled, definition) VALUES (
    'work-item.response', true,
    '{
      "calendarKey": "default-business",
      "targets": [
        {"metric": "response", "condition": "priority=CRITICAL", "targetMinutes": 15,  "warningBeforeMinutes": 5},
        {"metric": "response", "condition": "priority=HIGH",     "targetMinutes": 60,  "warningBeforeMinutes": 15},
        {"metric": "response", "condition": "priority=MEDIUM",   "targetMinutes": 240, "warningBeforeMinutes": 60},
        {"metric": "response", "condition": "priority=LOW",      "targetMinutes": 480, "warningBeforeMinutes": 120},
        {"metric": "resolution","condition": "priority=CRITICAL","targetMinutes": 240, "warningBeforeMinutes": 60},
        {"metric": "resolution","condition": "priority=HIGH",    "targetMinutes": 480, "warningBeforeMinutes": 120}
      ],
      "pauseStates": ["PENDING"]
    }'::jsonb
);

-- ---------------------------------------------------------------------------
-- Seed: sample automation rule (disabled by default)
-- ---------------------------------------------------------------------------
INSERT INTO automation_rule (rule_key, enabled, definition) VALUES (
    'notify-on-incident-created', false,
    '{
      "name": "Notify on incident created",
      "trigger": {"eventType": "incident.created"},
      "conditions": [
        {"field": "priority", "operator": "EQUALS", "value": "CRITICAL"}
      ],
      "actions": [
        {"type": "notify", "parameters": {"templateKey": "incident.created.critical", "channel": "IN_APP"}}
      ]
    }'::jsonb
);

-- ---------------------------------------------------------------------------
-- Seed: translations (RU / EN) for work-item labels
-- ---------------------------------------------------------------------------
INSERT INTO translation (namespace, translation_key, locale, value) VALUES
    ('object.work-item', 'label',              'en', 'Work Item'),
    ('object.work-item', 'label',              'ru', 'Рабочий элемент'),
    ('object.work-item', 'attr.title',         'en', 'Title'),
    ('object.work-item', 'attr.title',         'ru', 'Заголовок'),
    ('object.work-item', 'attr.description',   'en', 'Description'),
    ('object.work-item', 'attr.description',   'ru', 'Описание'),
    ('object.work-item', 'attr.service',       'en', 'Service'),
    ('object.work-item', 'attr.service',       'ru', 'Услуга'),
    ('object.work-item', 'attr.priority',      'en', 'Priority'),
    ('object.work-item', 'attr.priority',      'ru', 'Приоритет'),
    ('object.work-item', 'attr.state',         'en', 'State'),
    ('object.work-item', 'attr.state',         'ru', 'Состояние'),
    ('object.work-item', 'attr.type',          'en', 'Type'),
    ('object.work-item', 'attr.type',          'ru', 'Тип'),
    ('object.work-item', 'attr.assignee_id',   'en', 'Assignee'),
    ('object.work-item', 'attr.assignee_id',   'ru', 'Исполнитель'),
    ('object.work-item', 'attr.requester_id',  'en', 'Requester'),
    ('object.work-item', 'attr.requester_id',  'ru', 'Заявитель'),
    ('workflow.work-item', 'state.NEW',        'en', 'New'),
    ('workflow.work-item', 'state.NEW',        'ru', 'Новый'),
    ('workflow.work-item', 'state.IN_PROGRESS','en', 'In Progress'),
    ('workflow.work-item', 'state.IN_PROGRESS','ru', 'В работе'),
    ('workflow.work-item', 'state.PENDING',    'en', 'Pending'),
    ('workflow.work-item', 'state.PENDING',    'ru', 'Ожидание'),
    ('workflow.work-item', 'state.RESOLVED',   'en', 'Resolved'),
    ('workflow.work-item', 'state.RESOLVED',   'ru', 'Решён'),
    ('workflow.work-item', 'state.CLOSED',     'en', 'Closed'),
    ('workflow.work-item', 'state.CLOSED',     'ru', 'Закрыт'),
    ('workflow.work-item', 'state.CANCELLED',  'en', 'Cancelled'),
    ('workflow.work-item', 'state.CANCELLED',  'ru', 'Отменён'),
    ('workflow.work-item', 'transition.start',  'en', 'Start work'),
    ('workflow.work-item', 'transition.start',  'ru', 'Взять в работу'),
    ('workflow.work-item', 'transition.resolve','en', 'Resolve'),
    ('workflow.work-item', 'transition.resolve','ru', 'Решить'),
    ('workflow.work-item', 'transition.close',  'en', 'Close'),
    ('workflow.work-item', 'transition.close',  'ru', 'Закрыть'),
    ('form.work-item.default', 'section.main',       'en', 'Details'),
    ('form.work-item.default', 'section.main',       'ru', 'Основное'),
    ('form.work-item.default', 'section.assignment', 'en', 'Assignment'),
    ('form.work-item.default', 'section.assignment', 'ru', 'Назначение'),
    ('role', 'SERVICE_DESK_AGENT',   'en', 'Service Desk Agent'),
    ('role', 'SERVICE_DESK_AGENT',   'ru', 'Агент службы поддержки'),
    ('role', 'SERVICE_DESK_MANAGER', 'en', 'Service Desk Manager'),
    ('role', 'SERVICE_DESK_MANAGER', 'ru', 'Менеджер службы поддержки'),
    ('role', 'REQUESTER',            'en', 'Requester'),
    ('role', 'REQUESTER',            'ru', 'Заявитель'),
    ('role', 'ADMIN',                'en', 'Administrator'),
    ('role', 'ADMIN',                'ru', 'Администратор'),
    ('role', 'CHANGE_MANAGER',       'en', 'Change Manager'),
    ('role', 'CHANGE_MANAGER',       'ru', 'Менеджер изменений'),
    ('role', 'CAB_MEMBER',           'en', 'CAB Member'),
    ('role', 'CAB_MEMBER',           'ru', 'Участник CAB');
