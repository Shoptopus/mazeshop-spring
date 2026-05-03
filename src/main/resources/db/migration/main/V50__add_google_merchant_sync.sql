create table if not exists google_merchant_connections (
    id uuid primary key,
    store_id uuid not null references stores(id) on delete cascade,
    enabled boolean not null default false,
    merchant_id varchar(64),
    data_source_name varchar(255),
    data_source_display_name varchar(255),
    content_language varchar(8) not null default 'en',
    feed_label varchar(20) not null default 'GB',
    target_country varchar(8) not null default 'GB',
    service_account_json text,
    last_sync_at timestamptz,
    last_sync_status varchar(24),
    last_sync_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create unique index if not exists idx_google_merchant_connections_store_unique
    on google_merchant_connections(store_id)
    where deleted_at is null;

create table if not exists google_merchant_sync_runs (
    id uuid primary key,
    connection_id uuid not null references google_merchant_connections(id) on delete cascade,
    store_id uuid not null references stores(id) on delete cascade,
    triggered_by uuid references users(id) on delete set null,
    retry_of_run_id uuid references google_merchant_sync_runs(id) on delete set null,
    mode varchar(24) not null,
    status varchar(24) not null,
    requested_items integer not null default 0,
    synced_items integer not null default 0,
    failed_items integer not null default 0,
    summary text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create index if not exists idx_google_merchant_sync_runs_connection_created
    on google_merchant_sync_runs(connection_id, created_at desc);

create index if not exists idx_google_merchant_sync_runs_store_created
    on google_merchant_sync_runs(store_id, created_at desc);

create table if not exists google_merchant_sync_items (
    id uuid primary key,
    run_id uuid not null references google_merchant_sync_runs(id) on delete cascade,
    product_id uuid references products(id) on delete set null,
    variant_id uuid references product_variants(id) on delete set null,
    offer_id varchar(255) not null,
    title text,
    status varchar(24) not null,
    action varchar(24) not null default 'upsert',
    request_payload jsonb,
    response_payload jsonb,
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_google_merchant_sync_items_run_status
    on google_merchant_sync_items(run_id, status);
