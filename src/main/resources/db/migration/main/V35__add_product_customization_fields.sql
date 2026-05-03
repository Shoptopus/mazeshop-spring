alter table products
    add column if not exists customizable boolean not null default false,
    add column if not exists customization_text jsonb not null default '{}'::jsonb;

alter table cart_product
    add column if not exists customization_value text;

alter table order_product
    add column if not exists customization_value text;

alter table cart_product
    drop constraint if exists cart_product_cart_id_product_id_product_variant_id_key;

alter table order_product
    drop constraint if exists order_product_order_id_product_id_product_variant_id_key;

create unique index if not exists cart_product_line_identity_idx
    on cart_product (
        cart_id,
        product_id,
        coalesce(product_variant_id, '00000000-0000-0000-0000-000000000000'::uuid),
        coalesce(customization_value, '')
    );

create unique index if not exists order_product_line_identity_idx
    on order_product (
        order_id,
        product_id,
        coalesce(product_variant_id, '00000000-0000-0000-0000-000000000000'::uuid),
        coalesce(customization_value, '')
    );
