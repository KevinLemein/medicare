CREATE TABLE users (
                       id                  UUID PRIMARY KEY,
                       email               VARCHAR(255) NOT NULL,
                       firstname           VARCHAR(255) NOT NULL,
                       secondname           VARCHAR(255) NOT NULL,
                       role                VARCHAR(30)  NOT NULL,
                       status              VARCHAR(30)  NOT NULL,
                       failed_login_count  INTEGER      NOT NULL DEFAULT 0,
                       locked_until        TIMESTAMPTZ,
                       last_login_at       TIMESTAMPTZ,
                       created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT uq_users_email UNIQUE (email),

                       CONSTRAINT chk_users_email_normalized
                           CHECK (email = lower(trim(email))),

                       CONSTRAINT chk_users_role CHECK (role IN (
                                                                 'SYSTEM_ADMIN', 'DOCTOR', 'RECEPTIONIST', 'NURSE',
                                                                 'PHARMACIST', 'LAB_TECHNICIAN', 'ACCOUNTANT', 'PATIENT'
                           )),

                       CONSTRAINT chk_users_status CHECK (status IN (
                                                                     'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'
                           ))
);

-- Supports §17's "search/list users, view status" admin capability.
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role ON users (role);