alter table if exists orders
    add column if not exists store_id uuid references stores(id);

alter table if exists payments
    add column if not exists store_id uuid references stores(id);

update orders o
set store_id = coalesce(
    (
        select p.store_id
        from order_product oi
        join products p on p.id = oi.product_id
        where oi.order_id = o.id
          and p.store_id is not null
        order by oi.created_at asc
        limit 1
    ),
    (
        select dt.store_id
        from delivery_types dt
        where dt.id = o.delivery_type_id
    ),
    '22222222-2222-2222-2222-222222222222'
)
where o.store_id is null;

update payments p
set store_id = coalesce(
    (
        select o.store_id
        from orders o
        where p.payable_type = 'App\\Models\\Order'
          and p.payable_id = o.id::text
    ),
    '22222222-2222-2222-2222-222222222222'
)
where p.store_id is null;

alter table if exists orders
    alter column store_id set not null;

alter table if exists payments
    alter column store_id set not null;

create index if not exists idx_orders_store_id on orders(store_id);
create index if not exists idx_payments_store_id on payments(store_id);
