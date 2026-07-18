CREATE SCHEMA IF NOT EXISTS postgres_main;

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
