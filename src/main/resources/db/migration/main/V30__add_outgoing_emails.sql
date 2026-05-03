create table if not exists outgoing_emails (
    id uuid primary key,
    user_id uuid references users(id) on delete set null,
    recipient varchar(255) not null,
    subject varchar(255) not null,
    content text not null,
    status varchar(20) not null default 'queued',
    error_message text,
    attempts integer not null default 0,
    queued_at timestamptz not null,
    sent_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_outgoing_emails_status on outgoing_emails(status);
create index if not exists idx_outgoing_emails_queued_at on outgoing_emails(queued_at desc);
