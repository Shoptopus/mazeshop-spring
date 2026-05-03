create table if not exists stores (
    id uuid primary key,
    name varchar(160) not null,
    slug varchar(160) not null unique,
    description text,
    business_details text,
    logo jsonb,
    tagline varchar(255),
    owner_user_id uuid references users(id) on delete set null,
    owner_email varchar(150),
    owner_phone varchar(50),
    status varchar(20) not null default 'PENDING',
    rating_average numeric(5,2) not null default 0,
    rating_count integer not null default 0,
    deleted_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_stores_status on stores(status);
create index if not exists idx_stores_owner_user_id on stores(owner_user_id);

insert into stores(
    id,
    name,
    slug,
    description,
    business_details,
    logo,
    tagline,
    owner_user_id,
    owner_email,
    owner_phone,
    status,
    rating_average,
    rating_count,
    created_at,
    updated_at
)
select
    '22222222-2222-2222-2222-222222222222'::uuid,
    coalesce((
        select value
        from system_settings
        where key = 'shop.name'
          and deleted_at is null
        order by updated_at desc
        limit 1
    ), 'Default Store'),
    'default',
    null,
    null,
    null,
    coalesce((
        select value
        from system_settings
        where key = 'shop.tagline'
          and deleted_at is null
        order by updated_at desc
        limit 1
    ), ''),
    null,
    null,
    null,
    'ACTIVE',
    0,
    0,
    now(),
    now()
where not exists (select 1 from stores);

insert into permissions(name, created_at, updated_at) values
    ('can.view.stores', now(), now()),
    ('can.edit.stores', now(), now()),
    ('can.delete.stores', now(), now())
on conflict (name) do nothing;
