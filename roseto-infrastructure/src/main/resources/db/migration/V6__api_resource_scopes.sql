create table api_resource_scopes (
    resource_id uuid not null references api_resources(id) on delete cascade,
    name varchar(128) not null,
    primary key (resource_id, name)
);
