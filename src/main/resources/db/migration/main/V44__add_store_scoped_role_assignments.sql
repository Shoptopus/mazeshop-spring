create table if not exists user_store_roles (
    role_id bigint not null references roles(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    store_id uuid not null references stores(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (role_id, user_id, store_id)
);

insert into user_store_roles(role_id, user_id, store_id, created_at, updated_at)
select mr.role_id, mr.user_id, u.store_id, now(), now()
from model_has_roles mr
join users u on u.id = mr.user_id
join roles r on r.id = mr.role_id
where u.store_id is not null
  and r.name not in ('customer', 'super_admin')
on conflict do nothing;

delete from model_has_roles mr
using users u, roles r
where mr.user_id = u.id
  and mr.role_id = r.id
  and u.store_id is not null
  and r.name not in ('customer', 'super_admin');

create index if not exists idx_user_store_roles_user_store on user_store_roles(user_id, store_id);
create index if not exists idx_user_store_roles_store on user_store_roles(store_id);
