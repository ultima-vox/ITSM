# ADR 0001: Start as a modular monolith

**Status:** Accepted

We use one deployable Spring Boot application with Spring Modulith-verified package boundaries. Business modules own their model and persistence. Cross-module workflows use public services and transactional domain events. This minimizes operational coupling while retaining a deliberate extraction path. Extraction requires a measured ownership, availability, scaling, or security-isolation reason.
