alter table refresh_tokens add column expires_at timestamp with time zone not null default current_timestamp;
