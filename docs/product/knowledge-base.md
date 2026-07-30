# Knowledge Base baseline

Knowledge is versioned, localized and governed. An article moves Draft → In review → Published → Archived. Publication requires complete Russian content because Russian is the product's primary locale; other configured languages can be added independently to the same version. Published revisions are immutable. Corrections create a new revision and leave historical content available for audit.

`next_review_at` supports review governance. Feedback is stored against the exact article revision so quality metrics do not silently conflate old content with later corrections. Search projections should index only the latest published revision available to the caller's locale and permission scope.
