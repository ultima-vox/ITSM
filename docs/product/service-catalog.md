# Service Catalog baseline

A catalog item is a localized service offering—not merely a form link. It binds a versioned Form definition, a fulfillment Workflow and declarative Eligibility Rules. Publishing requires a complete Russian translation, form and workflow. Retiring an item stops new orders without destroying historical requests.

Eligibility is evaluated on the server against the authenticated subject and relevant organization/context data. A frontend may explain an ineligible state but cannot make the final decision. Request submission snapshots the catalog item version, form version and workflow version so later configuration changes do not rewrite fulfilment history.
