-- Seed the roles table with two default roles. Fixed UUIDs so the seed is reproducible across environments.
INSERT INTO roles (id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'ROLE_USER');
INSERT INTO roles (id, name) VALUES ('00000000-0000-0000-0000-000000000002', 'ROLE_ADMIN');
