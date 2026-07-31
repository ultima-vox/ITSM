# Wave 13 — CAB quorum · Search facets/J-K/recents · S7 path routes

**Date:** 2026-07-31  
**Branch:** `feat/naumen-depth-wave13`

## S9 CAB quorum

- Chair **Approve** needs ≥1 member `approve` vote (normal + emergency)
- Standard exempt
- Reject never blocked
- Board + drawer: disabled approve + quorum chip; store returns `changes.validation.cabQuorum`

## Search

- Facet counts from **full** corpus (filter client-side after)
- Recent searches (`vox-search-recents`)
- J / K navigate · Enter open · active row style

## S7 path routes

`/problems/:id`, `/changes/:id`, `/assets/:id`, `/knowledge/:id`, `/cmdb/:id` → module `?id=` / `?article=` / `?ci=` redirects.  
Search hit paths use path form for shareable URLs.
