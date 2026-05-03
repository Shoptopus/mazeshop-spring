alter table if exists event_logs
    add column if not exists store_id uuid;

alter table if exists audits
    add column if not exists store_id uuid;

update event_logs
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

update audits
set store_id = '22222222-2222-2222-2222-222222222222'
where store_id is null;

create index if not exists idx_event_logs_store_id on event_logs(store_id);
create index if not exists idx_event_logs_store_created_at on event_logs(store_id, created_at desc);
create index if not exists idx_audits_store_id on audits(store_id);
create index if not exists idx_audits_store_created_at on audits(store_id, created_at desc);
