-- Default rule: notify the work-item owner when its SLA enters the warning window.
-- The sla-warning-notify action is allowlisted by the servicedesk module; the rule fires on every
-- sla.warning event (emitted at most once per clock thanks to the warned_at marker).
INSERT INTO automation_rule (org_id, rule_key, enabled, definition, version, updated_at)
SELECT 'default', 'sla.warning.notify', true,
       '{
         "name": "Notify on SLA warning",
         "trigger": { "eventType": "sla.warning" },
         "conditions": [],
         "actions": [ { "type": "sla-warning-notify", "parameters": { "workItemId": "{{data.aggregateId}}" } } ]
       }'::jsonb,
       1, now()
WHERE NOT EXISTS (
  SELECT 1 FROM automation_rule WHERE org_id = 'default' AND rule_key = 'sla.warning.notify'
);
