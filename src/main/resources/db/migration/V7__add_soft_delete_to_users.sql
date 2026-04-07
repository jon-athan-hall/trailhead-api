-- Hibernate's @SoftDelete adds and manages this column. Default name is "deleted", default type is boolean.
ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
