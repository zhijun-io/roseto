create table api_resources (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    indicator varchar(512) not null unique,
    name varchar(256) not null,
    created_at timestamp with time zone not null
);
