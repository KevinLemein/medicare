-- Design intent (docs/services/identity-service.md) is ~320 chars to fit
-- any valid RFC 5321 email address; V1 shipped with VARCHAR(255). Widening
-- both users.email and account_provisioning_requests.requested_email
-- together — the latter stores the same normalized email value and would
-- otherwise silently truncate below what users.email now allows.

ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(320);
ALTER TABLE account_provisioning_requests ALTER COLUMN requested_email TYPE VARCHAR(320);
