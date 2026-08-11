CREATE TABLE catalog_bundle_component (
    bundle_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    component_item_id UUID NOT NULL REFERENCES catalog_item(id),
    org_id VARCHAR(128) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 100),
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (bundle_item_id, component_item_id),
    CHECK (bundle_item_id <> component_item_id)
);

CREATE INDEX catalog_bundle_component_org_idx
    ON catalog_bundle_component(org_id, bundle_item_id, position);

INSERT INTO catalog_item(id, org_id, item_key, status, form_definition_id, workflow_definition_id,
                         eligibility_rules, owner_id)
VALUES ('b1000000-0000-4000-8000-000000000004', 'default', 'employee-onboarding', 'PUBLISHED',
        'a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002',
        '[]'::jsonb, 'workplace-team')
ON CONFLICT DO NOTHING;

INSERT INTO catalog_item_translation(catalog_item_id, locale, name, description, category) VALUES
 ('b1000000-0000-4000-8000-000000000004','ru','Рабочее место нового сотрудника','Ноутбук, VPN и базовое ПО','Оборудование'),
 ('b1000000-0000-4000-8000-000000000004','en','New employee workplace','Laptop, VPN and standard software','Hardware')
ON CONFLICT DO NOTHING;

INSERT INTO catalog_bundle_component(bundle_item_id, component_item_id, org_id, quantity, position) VALUES
 ('b1000000-0000-4000-8000-000000000004','b1000000-0000-4000-8000-000000000002','default',1,10),
 ('b1000000-0000-4000-8000-000000000004','b1000000-0000-4000-8000-000000000001','default',1,20),
 ('b1000000-0000-4000-8000-000000000004','b1000000-0000-4000-8000-000000000003','default',1,30)
ON CONFLICT DO NOTHING;
