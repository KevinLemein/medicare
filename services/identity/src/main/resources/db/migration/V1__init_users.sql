CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL CHECK (role IN ('PATIENT', 'PHYSICIAN', 'ADMIN')),
                       enabled BOOLEAN NOT NULL DEFAULT true,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);