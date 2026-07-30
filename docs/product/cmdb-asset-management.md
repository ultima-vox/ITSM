# CMDB and Asset Management baseline

CMDB and Asset Management are connected but deliberately distinct. A Configuration Item represents an operational component or service and its directed dependency graph. An Asset represents an owned physical/software item and its financial/assignment lifecycle. An asset may reference a CI but neither model is a proxy for the other.

CI relationships are directional and typed; `source DEPENDS_ON target` supports deterministic impact traversal. Asset transitions are constrained: ordered → stock → in use/repair/retired; retired and lost assets cannot return to service. Every assignment, owner change and lifecycle transition must record an asset history entry, platform audit event and outbox event in the final application service.
