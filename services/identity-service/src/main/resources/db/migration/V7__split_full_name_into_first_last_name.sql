-- Design doc §14, v1.5. Renaming to snake_case, consistent with every
-- other column in this schema (failed_login_count, created_at, etc.) —
-- the original firstname/secondname columns predate that convention
-- being applied consistently.

ALTER TABLE users RENAME COLUMN firstname TO first_name;
ALTER TABLE users RENAME COLUMN secondname TO last_name;
