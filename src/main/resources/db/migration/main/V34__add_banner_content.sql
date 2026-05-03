alter table banners
    add column if not exists content jsonb not null default '{}'::jsonb;
