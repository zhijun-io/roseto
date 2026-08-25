create table user_sessions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    user_id uuid not null references users(id),
    device varchar(256) not null,
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    revoked_at timestamptz,
    constraint user_sessions_tenant_user_id unique (tenant_id, id)
);
create index user_sessions_user_idx on user_sessions (tenant_id, user_id, created_at desc);
