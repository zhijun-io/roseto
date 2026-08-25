create table roles (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    name varchar(128) not null,
    description varchar(512),
    created_at timestamptz not null,
    constraint roles_tenant_name_unique unique (tenant_id, name)
);
create index roles_tenant_idx on roles (tenant_id);
