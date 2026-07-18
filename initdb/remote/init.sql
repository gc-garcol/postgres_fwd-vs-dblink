CREATE SCHEMA IF NOT EXISTS postgres_remote;

CREATE TABLE IF NOT EXISTS postgres_remote.account_details (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,
    full_name TEXT,
    address TEXT,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO postgres_remote.account_details (external_id, full_name, address, phone) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Alice Anderson', '1 Apple St',    '+1-555-0001'),
    ('00000000-0000-0000-0000-000000000002', 'Bob Brown',      '2 Birch Ave',   '+1-555-0002'),
    ('00000000-0000-0000-0000-000000000003', 'Carol Clark',    '3 Cedar Rd',    '+1-555-0003'),
    ('00000000-0000-0000-0000-000000000004', 'Dave Davis',     '4 Dogwood Ln',  '+1-555-0004'),
    ('00000000-0000-0000-0000-000000000005', 'Erin Evans',     '5 Elm Blvd',    '+1-555-0005'),
    ('00000000-0000-0000-0000-000000000006', 'Frank Foster',   '6 Fir Ct',      '+1-555-0006'),
    ('00000000-0000-0000-0000-000000000007', 'Grace Green',    '7 Grove Way',   '+1-555-0007'),
    ('00000000-0000-0000-0000-000000000008', 'Henry Hill',     '8 Hazel Dr',    '+1-555-0008'),
    ('00000000-0000-0000-0000-000000000009', 'Ivy Irwin',      '9 Ivy Pl',      '+1-555-0009'),
    ('00000000-0000-0000-0000-000000000010', 'Jack Johnson',   '10 Juniper St', '+1-555-0010');
