CREATE TABLE audit_events (
                              id                UUID PRIMARY KEY,
                              event_type        VARCHAR(60) NOT NULL,
                              actor_user_id     UUID,
                              actor_client_id   VARCHAR(100),
                              subject_user_id   UUID,
                              occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                              metadata          JSONB,

                              CONSTRAINT fk_audit_events_actor_user
                                  FOREIGN KEY (actor_user_id) REFERENCES users (id),
                              CONSTRAINT fk_audit_events_subject_user
                                  FOREIGN KEY (subject_user_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_events_subject ON audit_events (subject_user_id, occurred_at);
CREATE INDEX idx_audit_events_type ON audit_events (event_type, occurred_at);