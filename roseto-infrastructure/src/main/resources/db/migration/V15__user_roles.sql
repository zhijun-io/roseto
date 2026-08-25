create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);
create index user_roles_role_idx on user_roles (role_id);
