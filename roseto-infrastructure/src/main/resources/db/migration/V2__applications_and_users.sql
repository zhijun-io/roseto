 create table applications (
     id uuid primary key,
     tenant_id uuid not null references tenants(id),
     client_id varchar(128) not null unique,
     client_secret_hash varchar(512),
     name varchar(256) not null,
     created_at timestamp with time zone not null
 );

create table redirect_uris (
    application_id uuid not null references applications(id) on delete cascade,
    redirect_uri varchar(2048) not null,
    primary key (application_id, redirect_uri)
);

create table users (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    username varchar(256) not null,
    password_hash varchar(512) not null,
    enabled boolean not null,
    created_at timestamp with time zone not null,
    unique (tenant_id, username)
);
