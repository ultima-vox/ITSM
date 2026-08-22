# Active Directory / LDAP federation (LDAPS)

ITSM never binds to the directory and never sees corporate passwords. Users authenticate
to Keycloak. The backend consumes a validated OIDC JWT (`iss`, `aud=itsm-backend`, `sub`,
`groups`) and maps groups through `group_role_mapping` (Flyway V82).

This document is operator configuration for **Keycloak User Federation**. It does not
embed a live directory hostname, bind password, or client secret.

## Realm files

| File | Used by | Demo users | Password grant |
| --- | --- | --- | --- |
| [`infra/keycloak/itsm-realm.json`](../../infra/keycloak/itsm-realm.json) | `docker-compose.yml` (local / CI) | `anna` / `admin` / `requester` | enabled |
| [`infra/keycloak/itsm-realm-prod.json`](../../infra/keycloak/itsm-realm-prod.json) | `docker-compose.prod.yml` | none | disabled |

Compose mounts **one** JSON file into `/opt/keycloak/data/import/itsm-realm.json`. Do not
mount the whole `infra/keycloak/` directory: Keycloak imports every `*.json` there, and
both files declare realm `itsm`.

`--import-realm` seeds a realm only when it does not already exist. After first start,
change federation through the admin console or kcadm, not by editing JSON and restarting.

## Connection (LDAPS only)

User Federation → Add Ldap provider. Vendor: **Active Directory**.

| Setting | Value |
| --- | --- |
| UI display name | operator-chosen, e.g. `corp-ad` |
| Vendor | Active Directory |
| Connection URL | `ldaps://<domain-controller-fqdn>:636` |
| Enable StartTLS | **Off** when the URL is already `ldaps://` |
| Use Truststore SPI | **Always** |
| Connection pooling | On |
| Pagination | **On** (page size 1000 unless the directory team specifies otherwise) |
| Connection timeout | 5000 ms (raise only if the directory team requires it) |
| Read timeout | 15000 ms |

Replace `<domain-controller-fqdn>` with the name the directory team gives you. Do not
point Keycloak at a global catalog port unless that team documents it.

### Certificate validation

LDAPS must present a certificate the Keycloak JVM trusts.

1. Obtain the CA that issues the domain controller certificate (enterprise PKI or the
   directory team's issuing CA).
2. Import that CA into the Keycloak truststore (`keytool -importcert` into the store
   mounted at the Keycloak `https-trust-store` / `KC_TRUSTSTORE_*` path).
3. Keep **Use Truststore SPI = Always**.
4. Do **not** enable “Always trust”, disable hostname verification, or ship a
   trust-all `X509TrustManager`.

If the controller certificate is wrong, federation must fail closed. Fix the truststore
or the directory certificate; do not skip validation.

## Bind account (least privilege)

| Setting | Value |
| --- | --- |
| Bind type | simple |
| Bind DN | `CN=<itsm-ldap-bind>,OU=<service-accounts>,<Base DN>` |
| Bind credential | secret store / Keycloak vault. Never git, images, or realm JSON. |

The bind account is a dedicated **read-only** service principal:

- Create / list / read properties on the users OU and the groups OU only.
- No write, no reset password, no “Account Operators”, no Domain Admins.
- Prefer a gMSA when the directory team supports it; otherwise a long random password
  rotated on the same schedule as other service secrets.
- Deny interactive logon.

## Directory DNs

All DNs are supplied by the directory team. Typical shape:

| Setting | Placeholder |
| --- | --- |
| Base DN | `DC=<domain>,DC=<tld>` |
| Users DN | `OU=<users>,<Base DN>` |
| Groups DN | `OU=<groups>,<Base DN>` |
| Username LDAP attribute | `sAMAccountName` (or `userPrincipalName` if that is the org standard) |
| RDN LDAP attribute | `cn` |
| UUID LDAP attribute | **`objectGUID`** |
| User object classes | `person, organizationalPerson, user` |
| Group object classes | `group` |
| Search scope | Subtree |
| Edit mode | **READ_ONLY** |

`objectGUID` is the stable external identifier. Do not use `sAMAccountName`, `cn`, or
`distinguishedName` as the UUID attribute.

## User attribute mappers

Keep Import Users **on**. Map:

| Keycloak | LDAP |
| --- | --- |
| username | `sAMAccountName` (or UPN, matching the username attribute above) |
| email | `mail` |
| firstName | `givenName` |
| lastName | `sn` |
| `organization_id` | operator-chosen AD attribute, or a hardcoded mapper with the tenant id |
| department | `department` (optional) |
| manager | `manager` (optional; stored as LDAP DN, not an ITSM user id) |

Add the built-in **MSAD User Account** mapper so `userAccountControl` drives Keycloak
`enabled`. A disabled AD account must not obtain a new token.

## Groups → ITSM roles

Create an LDAP **group-ldap-mapper**:

| Setting | Value |
| --- | --- |
| Groups DN | same Groups DN as above |
| Group name LDAP attribute | `cn` |
| Group object class | `group` |
| Membership LDAP attribute | `member` |
| Membership attribute type | DN |
| Mode | READ_ONLY |
| User groups retrieved by | `member` |
| Drop non-existing groups during sync | On |
| Ignore missing groups | Off for the `ITSM-*` set |

Target Keycloak groups (already in `itsm-realm-prod.json`):

| AD / Keycloak group CN | `group_role_mapping.idp_group` | ITSM `role.role_key` |
| --- | --- | --- |
| `ITSM-Users` | `ITSM-Users` | `REQUESTER` |
| `ITSM-ServiceDesk` | `ITSM-ServiceDesk` | `SERVICE_DESK_AGENT` |
| `ITSM-ServiceDesk-Managers` | `ITSM-ServiceDesk-Managers` | `SERVICE_DESK_MANAGER` |
| `ITSM-Change-Managers` | `ITSM-Change-Managers` | `CHANGE_MANAGER` |
| `ITSM-CAB` | `ITSM-CAB` | `CAB_MEMBER` |
| `ITSM-Admins` | `ITSM-Admins` | `ADMIN` |

JWT `groups` must carry those CNs (prod clients already have
`oidc-group-membership-mapper` with `full.path=false`). The backend matches
`group_role_mapping.idp_group` against `groups` and `realm_access.roles`. A bare
`ADMIN` role name does **not** grant `ADMIN`.

Mappings live in table `group_role_mapping` and can be changed without a code deploy.
Unknown groups grant nothing.

## Sync

| Setting | Starting point |
| --- | --- |
| Periodic full sync | 86400 s (daily) |
| Changed-users sync | 300–900 s |
| Sync Registrations | On (first login creates the Keycloak user) |
| Remove invalid users | On, so deletes in AD drop the federated Keycloak user |

ITSM `identity_account` upserts on each authenticated request from JWT `iss` + `sub`.
`sub` is the Keycloak user id. With **UUID LDAP attribute = objectGUID**, a rename or OU
move keeps the same Keycloak user, so `identity_account` does not duplicate.

Set `identity_account.enabled = false` to block an account inside ITSM even if AD is
still enabled. Historical tickets keep the old `subject_id`.

## Disabled AD users

1. AD `userAccountControl` → Keycloak user `enabled=false` (MSAD User Account mapper).
2. Keycloak refuses a new session. Existing access tokens die at `accessTokenLifespan`
   (300 s in the prod realm).
3. Optional: ITSM-side disable via `identity_account.enabled`.

Do not rely on SSO idle timeout alone for privileged lockout.

## Rename / move without duplicate principals

| Event | What must stay stable |
| --- | --- |
| `sAMAccountName` / UPN change | Keycloak user id (`sub`), LDAP UUID = `objectGUID` |
| User moved to another OU under Users DN | same |
| Display name / `cn` change | same |

After rename, username/email mappers update profile fields; `identity_account.external_id`
remains `sub`. If UUID LDAP attribute is `sAMAccountName`, a rename creates a **new**
Keycloak user and a duplicate ITSM principal — do not configure that.

## OTP for ADMIN

Realm JSON can enable `CONFIGURE_TOTP` (`itsm-realm-prod.json` does). It cannot attach
that required action to the `ADMIN` role only. Operators must bind a browser flow:

1. Authentication → Required actions → `CONFIGURE_TOTP` enabled (already in prod JSON).
2. Authentication → Flows → Duplicate **browser**.
3. In the copy, under **Browser – Conditional 2FA** (or a new conditional subflow after
   Username Password Form):
   - **Condition - user role** = `ADMIN` (REQUIRED).
   - **OTP Form** = REQUIRED when the condition matches.
4. Bind the copy as the realm Browser flow.
5. For every user who holds `ITSM-Admins` / `ADMIN`, set required action
   `CONFIGURE_TOTP` once so they enroll TOTP at next login.

SPA and backend stay on Authorization Code + PKCE. Password grant is off in the prod
realm; do not turn it back on.

## Break-glass Keycloak bootstrap admin

The Keycloak **master** bootstrap admin (`KC_ADMIN_USER` / `KC_ADMIN_PASSWORD`) is not an
ITSM `ADMIN` and is not in `itsm-realm-prod.json`. See [auth.md](./auth.md).

## Optional IdP brokering

Entra ID, ADFS, or another OIDC/SAML provider can be added as a Keycloak Identity
Provider in front of the same realm. Do not add a second password store inside ITSM.
Issuer, audience, and group mapping stay as above.

## Checks after first federation

- Bind as the read-only account from a host that must fail if the CA is untrusted.
- One test user in `ITSM-Users` can complete PKCE login; JWT `groups` contains
  `ITSM-Users`; ITSM grants `REQUESTER` only.
- A user in `ITSM-Admins` is forced through TOTP after the browser-flow bind.
- Disable that user in AD, wait for changed-users sync (or “Synchronize changed users”),
  confirm login fails.
- Rename `sAMAccountName` on a test account; confirm one `identity_account` row and the
  same `subject_id` on tickets.
- Password grant against `itsm-spa` / `itsm-backend` returns `unauthorized_client` or
  equivalent — direct access grants stay off.
