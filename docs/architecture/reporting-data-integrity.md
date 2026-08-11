# Reporting data integrity

Live reporting never fabricates chart points or reads browser mock activity. Workload totals come
from reporting-owned module contracts over PostgreSQL; seven-day opened/resolved trend derives only
from live work-item timestamps. Empty periods render zero values. CSAT uses submitted survey data
from backend trailing period, while SLA compliance uses live SLA state snapshots until a dedicated
historical projection is available.

Synthetic trend fill and proxy CSAT remain isolated to explicit mock mode and are visibly labelled.
CSV export contains currently filtered records fetched from API; it does not claim server-side
scheduled-report semantics.
