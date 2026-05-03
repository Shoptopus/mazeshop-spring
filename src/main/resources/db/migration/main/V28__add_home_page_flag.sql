alter table pages
    add column if not exists home_page boolean not null default false;

with first_enabled as (
    select id
    from pages
    where deleted_at is null
      and status = 1
    order by created_at asc
    limit 1
)
update pages
set home_page = true
where id in (select id from first_enabled)
  and not exists (
    select 1
    from pages existing
    where existing.deleted_at is null
      and existing.home_page = true
);
