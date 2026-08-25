create table tenants (
    id uuid primary key,
    public_key varchar(64) not null unique,
    name varchar(256) not null,
    issuer varchar(512) not null unique,
    created_at timestamp with time zone not null
);

create table management_accounts (
    id uuid primary key,
    username varchar(128) not null unique,
    password_hash varchar(512) not null,
    created_at timestamp with time zone not null
);
