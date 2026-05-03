alter table if exists stores
    add column if not exists contact_phone varchar(80);

alter table if exists stores
    add column if not exists contact_address text;

alter table if exists stores
    add column if not exists social_platforms jsonb;

alter table if exists stores
    add column if not exists theme jsonb;

update stores
set contact_phone = coalesce(
    contact_phone,
    (select nullif(value, '') from system_settings where key = 'shop.contact_phone' and deleted_at is null order by created_at desc limit 1)
)
where contact_phone is null;

update stores
set contact_address = coalesce(
    contact_address,
    (select nullif(value, '') from system_settings where key = 'shop.contact_address' and deleted_at is null order by created_at desc limit 1)
)
where contact_address is null;

update stores
set social_platforms = coalesce(
    social_platforms,
    (select nullif(value, '')::jsonb from system_settings where key = 'shop.social_platforms' and deleted_at is null order by created_at desc limit 1),
    '[]'::jsonb
)
where social_platforms is null;

update stores
set theme = coalesce(
    theme,
    (select nullif(value, '')::jsonb from system_settings where key = 'shop.theme' and deleted_at is null order by created_at desc limit 1)
)
where theme is null;
