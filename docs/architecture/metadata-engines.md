# Metadata engine contracts

The core is deliberately metadata-driven but not metadata-only: UI metadata improves configurability; the server remains the authority for validation, workflow and permissions.

| Engine | Contract / storage | Safety boundary |
|---|---|---|
| Object | `ObjectDefinition` / `object_definition` | Versioned definitions; business data remains module-owned |
| Form | `FormDefinition` / `form_definition` | CEL-only conditional expressions; no executable scripts |
| Workflow | `WorkflowDefinition` / `workflow_definition` | Explicit transitions and permissions |
| Automation | `AutomationRule` / `automation_rule` | Event triggers and allowlisted actions |
| RBAC | `PermissionChecker` / `rbac_grant` | Server-side decision, including field and object scope |
| SLA | `SlaPolicy` / `sla_policy` | Calendar, targets and pause states explicitly modelled |
| Notification | `NotificationRequest` | Recipient locale and channel policy applied centrally |
| Search | `SearchDocument` | Projection carries scope metadata; query is re-authorized |
| AI | `AiGateway` | Provider-neutral, authorized, advisory-only boundary |
