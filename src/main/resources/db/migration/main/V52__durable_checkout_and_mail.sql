alter table addresses alter column user_id drop not null;
alter table orders add column contact_email varchar(254);
alter table orders add column contact_name varchar(255);

create table system_mail_outbox (
 id uuid primary key,
 dedupe_key varchar(64) not null unique,
 recipient varchar(254) not null,
 subject varchar(255) not null,
 html_content text not null,
 status varchar(20) not null default 'queued',
 attempts integer not null default 0,
 next_attempt_at timestamptz not null default now(),
 claimed_at timestamptz,
 sent_at timestamptz,
 created_at timestamptz not null default now()
);
create index system_mail_outbox_pending on system_mail_outbox(status,next_attempt_at);

create table storefront_checkout_attempts (
 id uuid primary key,
 cart_id uuid not null,
 store_id uuid not null references stores(id),
 user_id uuid references users(id),
 order_id uuid not null references orders(id),
 payment_id uuid not null references payments(id),
 stripe_intent_id varchar(255) unique,
 client_secret text,
 access_token text not null,
 fingerprint varchar(64) not null,
 request jsonb not null,
 cart_lines jsonb not null,
 status varchar(20) not null default 'pending',
 expires_at timestamptz not null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index storefront_checkout_cart on storefront_checkout_attempts(cart_id,store_id,created_at desc);
create index storefront_checkout_pending on storefront_checkout_attempts(status,expires_at);
