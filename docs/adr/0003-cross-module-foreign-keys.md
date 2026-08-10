# ADR 0003: no cross-module foreign keys

Status: accepted.

Business modules own their tables. References to another module are opaque IDs, validated through
that module's public query/command contract. Runtime code and migrations must not read or constrain
another module's tables. Local foreign keys remain mandatory inside one module.

This permits independent persistence extraction and avoids deployment-order coupling. Referential
cleanup across modules uses lifecycle events plus reconciliation jobs; commands fail before writing
unknown references. V35 removes Problem Management's FK to Service Desk after replacing direct SQL
with `WorkItemReferenceQuery`. Same migration removes remaining Asset→CMDB,
Service Desk→CMDB/Storage, Catalog→Metadata/Workflow, and legacy Problem→Service Desk constraints;
their application services validate through published contracts.
