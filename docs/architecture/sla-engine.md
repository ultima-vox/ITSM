# SLA engine baseline

SLA is business-time based, not a simple timestamp subtraction. `WorkingCalendar` carries a timezone, permitted days, a same-day working window and explicit holidays. `SlaDeadlineCalculator` consumes only business minutes and therefore correctly crosses closed periods and weekends. The initial contract rejects negative targets and malformed calendars.

`SlaClock` represents live measurement state. A scheduler should query indexed RUNNING clocks, emit `sla.warning` / `sla.breached` through the transactional outbox, and write a `sla_clock_history` record on each start, pause, resume, achievement, breach or policy recalculation. A paused clock never silently loses elapsed history.
