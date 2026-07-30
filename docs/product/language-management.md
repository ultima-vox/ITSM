# Interface-language management

Russian (`ru`) is the default locale. A user has an independent locale preference exposed at `GET`/`PUT /api/v1/me/locale`; the authenticated OIDC subject, not a browser supplied identity, is the production subject source. The current foundation intentionally supports `ru`, `en` and `de` as configured locales.

Adding a language is a configuration operation: enable its locale, populate translation records for each namespace/key, run translation-completeness validation, then release it. Metadata labels resolve through `LocalizedText`; product UI bundles have the same key-completeness gate. Formatting must use the chosen BCP-47 locale, never fixed display strings.

A locale must not be offered before all required strings, plural rules, date/number/currency formats, templates, validation feedback and accessible labels are ready.
