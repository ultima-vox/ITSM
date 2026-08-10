# ADR 0002: Cross-module database constraints

Status: accepted

Business modules own their tables and runtime SQL. Another module must use public application
contracts or immutable events; it must not query or mutate an owner's tables directly.

PostgreSQL foreign keys across module-owned tables remain allowed as an integrity-only exception
inside this single deployment unit. They do not grant data ownership and must never become a
runtime query path. Removing a module therefore requires an explicit schema migration first.

Examples: Asset and Service Desk validate CI identifiers through `CmdbReferenceQuery`. Their link
tables may retain foreign keys to `configuration_item`, but only CMDB implementation executes SQL
against that table. Spring Modulith verification enforces Java dependencies; contract tests cover
the reference adapter.
