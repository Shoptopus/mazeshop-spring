alter table contents
    add column if not exists sort_order integer not null default 0;

create table if not exists pages (
    id uuid primary key,
    title jsonb not null,
    slug varchar(255) not null,
    status integer not null default 0,
    show_in_menu boolean not null default false,
    version integer not null default 1,
    deleted_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_pages_slug on pages(slug);
create index if not exists idx_pages_status on pages(status);
