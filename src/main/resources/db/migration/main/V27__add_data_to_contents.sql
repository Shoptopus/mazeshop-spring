alter table contents
    add column if not exists data jsonb not null default '{}'::jsonb;
