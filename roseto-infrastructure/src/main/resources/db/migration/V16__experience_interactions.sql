create table experience_interactions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    identifier varchar(320) not null,
    mode varchar(32) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint experience_interactions_tenant_id unique (tenant_id, id)
);
create index experience_interactions_status_idx
    on experience_interactions (tenant_id, status, updated_at desc);

alter table verification_records add column interaction_id uuid references experience_interactions(id);
create index verification_records_interaction_idx on verification_records (tenant_id, interaction_id);

alter table experience_interactions add column client_id varchar(128);
alter table experience_interactions add column redirect_uri varchar(2048);
alter table experience_interactions add column code_challenge varchar(128);
alter table experience_interactions add column nonce varchar(512);
alter table experience_interactions add column scope varchar(1000) not null default 'openid';
alter table experience_interactions add column resource varchar(512);
