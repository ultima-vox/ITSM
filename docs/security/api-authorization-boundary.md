# API authorization boundary

Authentication alone never authorizes an ITSM operation. Every `/api/**` controller must expose
one recognized authorization boundary:

- inject `AccessControl` and perform operation/record checks;
- use `@SelfScopedEndpoint` and derive every record scope from authenticated subject;
- use `@GuardedEndpoint` when a dedicated policy gateway performs equivalent checks.

`ApiAuthorizationBoundaryInterceptor` enforces this invariant before handler execution. A new
controller with no recognized boundary fails closed with `AccessDeniedException`. A classpath test
enumerates every `@RestController`, so an unguarded adapter also fails CI before deployment.

This safety net complements, not replaces, negative permission matrices and record/tenant checks.
Public health and disabled-by-default production OpenAPI routes do not pass through `/api/**`.
