create table application_api_resources (
    application_id uuid not null references applications(id) on delete cascade,
    resource_id uuid not null references api_resources(id) on delete cascade,
    primary key (application_id, resource_id)
);
