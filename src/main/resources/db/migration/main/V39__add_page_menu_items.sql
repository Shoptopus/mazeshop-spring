create table if not exists page_menu_items (
    id uuid primary key,
    page_id uuid references pages(id) on delete cascade,
    parent_id uuid references page_menu_items(id) on delete cascade,
    item_type varchar(20) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists ux_page_menu_items_page_id
    on page_menu_items(page_id)
    where page_id is not null;

create unique index if not exists ux_page_menu_items_shop
    on page_menu_items(item_type)
    where item_type = 'shop';

create index if not exists idx_page_menu_items_parent_sort
    on page_menu_items(parent_id, sort_order, created_at);
