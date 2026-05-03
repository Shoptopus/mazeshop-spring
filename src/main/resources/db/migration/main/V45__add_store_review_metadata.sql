alter table if exists stores
    add column if not exists review_notes text;

alter table if exists stores
    add column if not exists status_reason text;
