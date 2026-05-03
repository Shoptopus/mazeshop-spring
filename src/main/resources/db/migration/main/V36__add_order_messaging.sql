create table if not exists order_message_threads (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete cascade,
    customer_user_id uuid references users(id) on delete set null,
    last_message_preview text,
    last_message_at timestamptz,
    last_message_sender_role varchar(20),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique(order_id)
);

create table if not exists order_messages (
    id uuid primary key,
    thread_id uuid not null references order_message_threads(id) on delete cascade,
    sender_user_id uuid references users(id) on delete set null,
    sender_role varchar(20) not null,
    body text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists order_message_threads_last_message_at_idx
    on order_message_threads(last_message_at desc nulls last, created_at desc);

create index if not exists order_messages_thread_created_at_idx
    on order_messages(thread_id, created_at asc);

insert into permissions(name, created_at, updated_at) values
    ('messages.view', now(), now()),
    ('messages.manage', now(), now())
on conflict (name) do nothing;
