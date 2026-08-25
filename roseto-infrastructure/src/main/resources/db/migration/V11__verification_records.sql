create table verification_records (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    user_id uuid references users(id),
    purpose varchar(64) not null,
    identifier varchar(320) not null,
    code_hash varchar(128) not null,
    expires_at timestamptz not null,
    attempts integer not null default 0,
    max_attempts integer not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint verification_records_attempts_check check (attempts >= 0 and attempts <= max_attempts)
);
create index verification_records_tenant_idx on verification_records (tenant_id, id);
create index verification_records_expiry_idx on verification_records (status, expires_at);
