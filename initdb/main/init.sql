CREATE SCHEMA IF NOT EXISTS postgres_main;

-- Last remote-db config applied by the setup runners; they skip provisioning
-- when the stored value matches application.yaml.
CREATE TABLE IF NOT EXISTS postgres_main.system_configs (
    config_key   VARCHAR(128) PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS postgres_main.users (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO postgres_main.users (external_id, username, email) VALUES
    ('00000000-0000-0000-0000-000000000001', 'alice',   'alice@example.com'),
    ('00000000-0000-0000-0000-000000000002', 'bob',     'bob@example.com'),
    ('00000000-0000-0000-0000-000000000003', 'carol',   'carol@example.com'),
    ('00000000-0000-0000-0000-000000000004', 'dave',    'dave@example.com');
