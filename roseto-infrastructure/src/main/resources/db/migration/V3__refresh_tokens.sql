create table refresh_tokens (
    token varchar(512) primary key,
    user_id uuid not null,
    client_id varchar(128) not null,
    tenant_id uuid not null,
    scope varchar(2048) not null,
    revoked boolean not null default false,
    created_at timestamp with time zone not null
);
create index refresh_tokens_client_idx on refresh_tokens(client_id);
