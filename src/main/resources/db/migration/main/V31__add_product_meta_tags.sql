alter table products
    add column if not exists meta_tags jsonb not null default '[]'::jsonb;
