-- Persists the RSA keypair used to sign issued JWTs, so tokens survive a
-- service restart. A single fixed-id row ("primary"); see SigningKeyService
-- for the race-safe generate-or-load logic.
CREATE TABLE signing_keys (
                              id            VARCHAR(50) NOT NULL,
                              key_id        VARCHAR(100) NOT NULL,
                              private_key   BYTEA NOT NULL,
                              public_key    BYTEA NOT NULL,
                              created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                              PRIMARY KEY (id)
);
