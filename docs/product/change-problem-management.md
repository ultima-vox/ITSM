# Change and Problem Management baseline

Change and Problem are separate module-owned aggregates. A Problem accumulates related incidents and records root cause/workaround independently from ticket resolution. A Change carries risk, an implementation plan, a tested rollback plan, schedule and approvals.

The initial `Change` aggregate enforces a non-bypassable lifecycle: Draft → Assessment → Authorization → Scheduled → Implementing → Review → Closed. A normal change cannot be scheduled without an explicit approval decision. Workflow metadata may introduce additional approvals, CAB policy, segregation-of-duties checks and maintenance-window validation; it cannot remove these core invariants.
