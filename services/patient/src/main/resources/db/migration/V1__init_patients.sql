CREATE TABLE patients (
                          id UUID PRIMARY KEY,
                          user_id UUID NOT NULL UNIQUE,
                          date_of_birth DATE,
                          phone_number VARCHAR(20),
                          address TEXT,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_user_id ON patients(user_id);

CREATE TABLE emergency_contacts (
                                    id UUID PRIMARY KEY,
                                    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                                    name VARCHAR(255) NOT NULL,
                                    phone VARCHAR(20) NOT NULL,
                                    relationship_type VARCHAR(20) NOT NULL
                                        CHECK (relationship_type IN ('SPOUSE','PARENT','CHILD','SIBLING','FRIEND','OTHER'))
);

CREATE INDEX idx_emergency_contacts_patient_id ON emergency_contacts(patient_id);

CREATE TABLE allergies (
                           id UUID PRIMARY KEY,
                           patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                           allergen VARCHAR(255) NOT NULL,
                           notes TEXT
);

CREATE INDEX idx_allergies_patient_id ON allergies(patient_id);