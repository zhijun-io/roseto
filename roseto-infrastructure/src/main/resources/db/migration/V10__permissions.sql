create table permissions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    resource_id uuid not null references api_resources(id),
    name varchar(128) not null,
    description varchar(512),
    created_at timestamptz not null,
    constraint permissions_tenant_resource_name_unique unique (tenant_id, resource_id, name)
);
create index permissions_tenant_resource_idx on permissions (tenant_id, resource_id);
