alter table banners
    add column if not exists button_text jsonb not null default '{}'::jsonb,
    add column if not exists url text;
