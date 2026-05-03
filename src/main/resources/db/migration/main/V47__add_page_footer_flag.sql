alter table if exists pages
    add column if not exists add_to_footer boolean not null default false;
