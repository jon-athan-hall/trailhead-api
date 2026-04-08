-- Add audit columns to users. Nullable because registrations and the seeded admin happen without an authenticated user.
ALTER TABLE users ADD COLUMN created_by VARCHAR(36) NULL;
ALTER TABLE users ADD COLUMN updated_by VARCHAR(36) NULL;

-- Add audit columns to roles. Nullable because the default roles are seeded by Flyway, not by an authenticated user.
ALTER TABLE roles ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE roles ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE roles ADD COLUMN created_by VARCHAR(36) NULL;
ALTER TABLE roles ADD COLUMN updated_by VARCHAR(36) NULL;
