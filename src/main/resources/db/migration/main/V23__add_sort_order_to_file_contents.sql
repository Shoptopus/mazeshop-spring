alter table file_contents
    add column if not exists sort_order integer not null default 0;
