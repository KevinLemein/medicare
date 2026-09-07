CREATE TABLE account_provisioning_requests (
                                               idempotency_key   UUID PRIMARY KEY,
                                               outcome           VARCHAR(20) NOT NULL,
                                               user_id           UUID,
                                               requested_email   VARCHAR(255) NOT NULL,
                                               resolved_at       TIMESTAMPTZ,
                                               created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

                                               CONSTRAINT fk_provisioning_requests_user
                                                   FOREIGN KEY (user_id) REFERENCES users (id),

                                               CONSTRAINT chk_provisioning_requests_outcome
                                                   CHECK (outcome IN ('CREATED', 'CONFLICT')),

                                               CONSTRAINT chk_provisioning_requests_outcome_user_id
                                                   CHECK (
                                                       (outcome = 'CREATED'  AND user_id IS NOT NULL) OR
                                                       (outcome = 'CONFLICT' AND user_id IS NULL)
                                                       ),

                                               CONSTRAINT chk_provisioning_requests_email_normalized
                                                   CHECK (requested_email = lower(trim(requested_email)))
);

CREATE INDEX idx_provisioning_requests_outcome_created
    ON account_provisioning_requests (outcome, created_at, resolved_at);