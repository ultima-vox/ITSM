# Notification preferences

Notification preferences are stored by trusted organization context plus authenticated subject.
`GET/PUT /api/v1/me/notification-preferences` is self-scoped: request bodies cannot select another
user or tenant. Defaults are server-defined; updates are upserts with audit and outbox events.

Frontend keeps a local non-sensitive cache for immediate notification filtering and mock mode, then
hydrates from server in live mode. Save failures surface to operator and never claim remote success.
Delivery checks preferences before persistence/fan-out. Email opt-out applies to email channel;
SLA/breach, assignment/owner, and mention templates respect their category flags. In-app messages
outside opted-out categories remain available. Desktop preference stays a browser presentation choice.
