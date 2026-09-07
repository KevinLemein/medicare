CREATE TABLE password_credentials (
                                      id             UUID PRIMARY KEY,
                                      user_id        UUID NOT NULL,
                                      password_hash  VARCHAR(255) NOT NULL,
                                      algorithm      VARCHAR(50)  NOT NULL DEFAULT 'ARGON2ID',
                                      updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                      CONSTRAINT uq_password_credentials_user_id UNIQUE (user_id),
                                      CONSTRAINT fk_password_credentials_user
                                          FOREIGN KEY (user_id) REFERENCES users (id)
);