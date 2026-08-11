# Field and state access policies

`field_access_policy` adds backend-enforced field authorization by organization,
object type, field, operation, target/current state, and required permission.

Rules under organization `*` are platform defaults. If current organization defines
one or more rules for same object/field/operation, those tenant rules replace global
rules. Once any policy exists, evaluation is fail-closed: state must match and subject
must hold at least one required permission. Fields without policy retain normal
object-operation authorization.

Initial protected slice covers Service Desk resolution fields. `resolutionCode` and
`resolutionNotes` may be written only for target state `RESOLVED` by subjects holding
`work-item.resolve`. ADMIN, Service Desk Agent, and Service Desk Manager receive that
permission. Requester and Change Manager do not.

Every protected controller must call `FieldAccessControl` in addition to object-level
authorization. Frontend visibility is presentation only and cannot grant access.
