# Keycloak — ITSM realm

Realm JSON: [`itsm-realm.json`](./itsm-realm.json)

Imported automatically by `docker compose` via:

```yaml
keycloak:
  command: start-dev --import-realm
  volumes:
    - ./infra/keycloak:/opt/keycloak/data/import
```

Keycloak only imports a realm when it does **not** already exist. To re-import after editing the JSON:

```bash
docker compose stop keycloak
docker compose rm -f keycloak
docker compose up -d keycloak
```

(`start-dev` uses an ephemeral/embedded store inside the container, so removing the container clears realms.)

## Admin console

| | |
| --- | --- |
| URL | http://localhost:8081 |
| Master admin | `admin` / `admin` |
| Realm | **itsm** (switch realm in the top-left selector) |
| Issuer | `http://localhost:8081/realms/itsm` |

## Demo users (realm `itsm`)

| Username | Password | Realm roles | Fixed subject (`sub`) |
| --- | --- | --- | --- |
| `anna` | `anna` | `SERVICE_DESK_AGENT` | `a0000000-0000-4000-8000-000000000001` |
| `admin` | `admin` | `ADMIN` | `a0000000-0000-4000-8000-000000000002` |
| `requester` | `requester` | `REQUESTER` | `a0000000-0000-4000-8000-000000000003` |

`anna` is **Anna Yakovleva** (`anna@itsm.local`).

Realm roles mirror platform RBAC keys from Flyway (`ADMIN`, `SERVICE_DESK_AGENT`, `SERVICE_DESK_MANAGER`, `REQUESTER`, `CHANGE_MANAGER`, `CAB_MEMBER`). Tokens carry them under `realm_access.roles` (Keycloak default roles client scope). The backend maps each role to a Spring authority `ROLE_<name>` (e.g. `ROLE_ADMIN`, `ROLE_SERVICE_DESK_AGENT`).

DB seed migration **V14** links the fixed Keycloak user IDs above into `principal_role` so `RbacPermissionChecker` resolves permissions without relying only on JWT role names.

## Clients

| Client ID | Type | Notes |
| --- | --- | --- |
| `itsm-spa` | Public | Authorization code + PKCE; redirect `http://localhost:5173/*`; web origins include `http://localhost:5173` and `127.0.0.1`. Direct access grants enabled for local testing. |
| `itsm-backend` | Confidential | Secret `itsm-backend-secret`. Direct access grants for password-grant scripts. Optional audience mapper adds `itsm-backend` to access tokens. |

Backend resource server config (no audience enforced by default):

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8081/realms/itsm
```

## Get an access token (password grant)

### Public SPA client

```bash
curl -s -X POST "http://localhost:8081/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=itsm-spa" \
  -d "username=anna" \
  -d "password=anna"
```

### Confidential backend client

```bash
curl -s -X POST "http://localhost:8081/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=itsm-backend" \
  -d "client_secret=itsm-backend-secret" \
  -d "username=admin" \
  -d "password=admin"
```

### PowerShell

```powershell
$body = @{
  grant_type = "password"
  client_id  = "itsm-spa"
  username   = "anna"
  password   = "anna"
}
$tok = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/realms/itsm/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body $body
$tok.access_token
```

### Call the API

```bash
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=itsm-spa&username=admin&password=admin" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/work-items
```

Inspect claims (JWT middle segment, base64url):

```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
# expect: "realm_access": { "roles": [ "ADMIN" ] }  (or SERVICE_DESK_AGENT / REQUESTER)
```

## OIDC discovery

```text
http://localhost:8081/realms/itsm/.well-known/openid-configuration
```
