# Identity Service

## Purpose

`identity-service` is the Medicare platform's authentication and identity
authority. It owns the only table of users in the system and is the single
OAuth2/OIDC Authorization Server that every other service and the frontend
trust for logins and access tokens. No other service stores credentials or
issues tokens.

Concretely, it owns:

- User accounts, roles, and account lifecycle status (pending activation,
  active, suspended, deactivated)
- Password credentials (hashed, never stored or logged in plain text)
- Activation and password-reset tokens
- OAuth2/OIDC client registrations, authorizations, and consents
- An append-only audit trail of identity-related events

## Tech stack

- Java 25 (Gradle toolchain), Spring Boot 4.1.1
- Spring Security + Spring Authorization Server (OAuth2/OIDC)
- Spring Data JPA / Hibernate, with Flyway owning the schema
  (`hibernate.ddl-auto=validate` — Hibernate never creates or alters tables)
- PostgreSQL 18 (via Docker Compose in `infrastructure/`)
- Lombok, Bouncy Castle (crypto), Nimbus JOSE/JWT (JWK/RSA signing)

## Roles

Defined in `entity/Role.java`: `SYSTEM_ADMIN`, `DOCTOR`, `RECEPTIONIST`,
`NURSE`, `PHARMACIST`, `LAB_TECHNICIAN`, `ACCOUNTANT`, `PATIENT`. A user's
role is embedded as a custom `role` claim in issued JWTs and converted to a
Spring Security `ROLE_*` authority by resource servers (see
`config/SecurityConfig.jwtAuthenticationConverter`).

## Data model (Flyway migrations, `src/main/resources/db/migration`)

| Migration | Table | Purpose |
|---|---|---|
| V1 | `users` | Core account: email, name, role, status, login/lockout tracking |
| V2 | `password_credentials` | Hashed password, kept separate from `users` |
| V3 | `account_tokens` | Single-use activation / password-reset tokens (stored hashed, with expiry) |
| V4 | `account_provisioning_requests` | Idempotency ledger for patient-account provisioning requests from `patient-service` |
| V5 | `audit_events` | Append-only log of identity events (login, lockout, password change, role change, ...) |
| V6 | OAuth2 authorization server tables | Spring Authorization Server's own schema: `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent` |
| V7 | `users` | Splits a single `full_name` column into `first_name` / `last_name` |

## Two security filter chains

- **`AuthorizationServerConfig`** (`@Order(1)`) — the OAuth2/OIDC protocol
  endpoints (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, OIDC
  discovery, etc.), matched only against the Authorization Server's own
  endpoint matcher.
- **`SecurityConfig`** (`@Order(2)`) — everything else: the hosted login
  page, and the public account-management endpoints below. All other
  requests require authentication.

JWTs are signed with an in-memory RSA keypair generated fresh on every
application start (`AuthorizationServerConfig.jwkSource`). This means
**access/refresh tokens issued before a restart stop validating after a
restart** — acceptable for local development, but a persisted/rotated key
would be needed before this goes anywhere near production.

## REST endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/accounts/activate` | GET | public | Validate an activation token without consuming it |
| `/accounts/activate` | POST | public | Consume an activation token and set the account's first password |
| `/accounts/password-reset/request` | POST | public | Request a reset link (always returns the same response, whether or not the email exists) |
| `/accounts/password-reset/confirm` | POST | public | Consume a reset token and set a new password |
| `/accounts/password/change` | POST | authenticated | Change the current user's password |
| `/oauth2/*`, `/.well-known/*` | — | protocol-specific | Standard OAuth2/OIDC Authorization Server endpoints |

Application logic beyond these HTTP entry points (staff account creation,
patient account provisioning, suspend/reactivate/deactivate, role changes,
account unlock) lives in `UserService` but isn't yet exposed over HTTP —
those are admin/service-to-service operations still being wired up.

## Registered OAuth2 clients

Seeded idempotently on startup by `ClientRegistrationInitializer` (deliberately
not a Flyway migration, since a client secret has no place in version
control):

- **`hms-frontend`** — public client (no secret), `authorization_code` +
  `refresh_token` grants, PKCE required, no consent screen (first-party).
- **`patient-service`** — confidential client, `client_credentials` grant,
  scope `identity:provision-patient`, used by `patient-service` to call the
  (not-yet-built) account-provisioning endpoint when a new patient record is
  created there.

Both clients get a 5-minute access token lifetime.

## First-run bootstrap

`AdminBootstrapRunner` creates exactly one `SYSTEM_ADMIN` user on first
startup, keyed by the unique `email` constraint (`INSERT ... ON CONFLICT DO
NOTHING`) so concurrent instances starting against an empty database can't
create two admins. The generated activation link is printed to stdout, not
emailed (there's no notification service yet).

## Configuration

Configured via environment variables (see `.env.example`), loaded through
`application.yml`:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection — must match `IDENTITY_DB_*` in `infrastructure/.env` |
| `SERVER_PORT` | HTTP port (default `8081`) |
| `OAUTH2_ISSUER_URI` | This service's own issuer URI, embedded in issued tokens and OIDC discovery |
| `FRONTEND_REDIRECT_URI` | Redirect URI registered for the `hms-frontend` client |
| `PATIENT_SERVICE_CLIENT_SECRET` | Secret for the `patient-service` client — no default, fails startup if unset |
| `BOOTSTRAP_ADMIN_EMAIL` | Email for the first-run `SYSTEM_ADMIN` account — no default, fails startup if unset |

## Running locally

```bash
# 1. Start the database (from infrastructure/)
docker compose up -d

# 2. Copy and fill in the service's own env file
cp services/identity-service/.env.example services/identity-service/.env

# 3. Run the service
cd services/identity-service
export $(grep -v '^#' .env | xargs -d '\n')
./gradlew bootRun
```

Verify it's up:

```bash
curl http://localhost:8081/.well-known/oauth-authorization-server
```

## Known gaps / follow-ups

- Signing key is regenerated on every restart (not persisted) — tokens
  don't survive a restart.
- The account-provisioning endpoint `patient-service` is meant to call
  (`identity:provision-patient` scope) isn't exposed over HTTP yet, even
  though the service-layer logic (`UserService.provisionPatientAccount`)
  and its idempotency table already exist.
- Notifications (activation links, reset links) are logged to stdout only;
  there's no notification-service integration yet.
