INSERT INTO automation_rule (org_id, rule_key, enabled, definition, version, updated_at)
SELECT 'default', 'sla.escalate.breach', true,
       '{
         "name": "Escalate breached SLA",
         "trigger": { "eventType": "sla.breached" },
         "conditions": [],
         "actions": [ { "type": "escalate", "parameters": { "workItemId": "{{data.aggregateId}}" } } ]
       }'::jsonb,
       1, now()
WHERE NOT EXISTS (
  SELECT 1 FROM automation_rule WHERE org_id = 'default' AND rule_key = 'sla.escalate.breach'
);
