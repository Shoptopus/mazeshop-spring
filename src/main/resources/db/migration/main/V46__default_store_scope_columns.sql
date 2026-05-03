alter table if exists products
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists product_attributes
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists product_attribute_options
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists delivery_types
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists delivery_rules
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists discount_rules
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists voucher_codes
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists pages
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists page_menu_items
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists ratings
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists banners
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists notifications
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists orders
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;

alter table if exists payments
    alter column store_id set default '22222222-2222-2222-2222-222222222222'::uuid;
