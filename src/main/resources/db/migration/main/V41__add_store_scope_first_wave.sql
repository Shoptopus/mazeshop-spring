alter table if exists users
    add column if not exists store_id uuid references stores(id);

alter table if exists products
    add column if not exists store_id uuid references stores(id);

alter table if exists product_attributes
    add column if not exists store_id uuid references stores(id);

alter table if exists product_attribute_options
    add column if not exists store_id uuid references stores(id);

alter table if exists delivery_types
    add column if not exists store_id uuid references stores(id);

alter table if exists delivery_rules
    add column if not exists store_id uuid references stores(id);

alter table if exists discount_rules
    add column if not exists store_id uuid references stores(id);

alter table if exists voucher_codes
    add column if not exists store_id uuid references stores(id);

alter table if exists pages
    add column if not exists store_id uuid references stores(id);

alter table if exists page_menu_items
    add column if not exists store_id uuid references stores(id);

alter table if exists ratings
    add column if not exists store_id uuid references stores(id);

alter table if exists banners
    add column if not exists store_id uuid references stores(id);

alter table if exists notifications
    add column if not exists store_id uuid references stores(id);

update users u
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null
  and exists (
    select 1
    from model_has_roles mr
    where mr.user_id = u.id
  );

update products
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update product_attributes
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update product_attribute_options o
set store_id = coalesce(
    (select a.store_id from product_attributes a where a.id = o.product_attribute_id),
    '22222222-2222-2222-2222-222222222222'
)
where store_id is null;

update delivery_types
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update delivery_rules r
set store_id = coalesce(
    (select t.store_id from delivery_types t where t.id = r.delivery_type_id),
    '22222222-2222-2222-2222-222222222222'
)
where store_id is null;

update discount_rules
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update voucher_codes
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update pages
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update page_menu_items
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update ratings
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update banners
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update notifications
set store_id = coalesce(
    (select u.store_id from users u where u.id = notifications.user_id),
    '22222222-2222-2222-2222-222222222222'
)
where store_id is null;

alter table if exists products
    alter column store_id set not null;

alter table if exists product_attributes
    alter column store_id set not null;

alter table if exists product_attribute_options
    alter column store_id set not null;

alter table if exists delivery_types
    alter column store_id set not null;

alter table if exists delivery_rules
    alter column store_id set not null;

alter table if exists discount_rules
    alter column store_id set not null;

alter table if exists voucher_codes
    alter column store_id set not null;

alter table if exists pages
    alter column store_id set not null;

alter table if exists page_menu_items
    alter column store_id set not null;

alter table if exists ratings
    alter column store_id set not null;

alter table if exists banners
    alter column store_id set not null;

drop index if exists ux_page_menu_items_page_id;
create unique index if not exists ux_page_menu_items_page_id
    on page_menu_items(store_id, page_id)
    where page_id is not null;

drop index if exists ux_page_menu_items_shop;
create unique index if not exists ux_page_menu_items_shop
    on page_menu_items(store_id, item_type)
    where item_type = 'shop';

create index if not exists idx_users_store_id on users(store_id);
create index if not exists idx_products_store_id on products(store_id);
create index if not exists idx_product_attributes_store_id on product_attributes(store_id);
create index if not exists idx_product_attribute_options_store_id on product_attribute_options(store_id);
create index if not exists idx_delivery_types_store_id on delivery_types(store_id);
create index if not exists idx_delivery_rules_store_id on delivery_rules(store_id);
create index if not exists idx_discount_rules_store_id on discount_rules(store_id);
create index if not exists idx_voucher_codes_store_id on voucher_codes(store_id);
create index if not exists idx_pages_store_id on pages(store_id);
create index if not exists idx_page_menu_items_store_parent on page_menu_items(store_id, parent_id, sort_order, created_at);
create index if not exists idx_ratings_store_id on ratings(store_id);
create index if not exists idx_banners_store_id on banners(store_id);
create index if not exists idx_notifications_store_id on notifications(store_id);
