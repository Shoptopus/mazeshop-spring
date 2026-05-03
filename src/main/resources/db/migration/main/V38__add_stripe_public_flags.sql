insert into payment_provider_configs(payment_provider_id, setting, value, test_value, public_value, created_at, updated_at)
select provider.id, 'live_public', 'true', null, false, now(), now()
from payment_providers provider
where lower(provider.name) = 'stripe'
  and not exists (
    select 1 from payment_provider_configs cfg
    where cfg.payment_provider_id = provider.id
      and cfg.setting = 'live_public'
  );

insert into payment_provider_configs(payment_provider_id, setting, value, test_value, public_value, created_at, updated_at)
select provider.id, 'test_public', 'true', null, false, now(), now()
from payment_providers provider
where lower(provider.name) = 'stripe'
  and not exists (
    select 1 from payment_provider_configs cfg
    where cfg.payment_provider_id = provider.id
      and cfg.setting = 'test_public'
  );

update payment_provider_configs cfg
set value = case when cfg.public_value then 'true' else 'false' end,
    updated_at = now()
from payment_providers provider
where cfg.payment_provider_id = provider.id
  and lower(provider.name) = 'stripe'
  and cfg.setting = 'live_public';

update payment_provider_configs cfg
set value = case when cfg.public_value then 'true' else 'false' end,
    updated_at = now()
from payment_providers provider
where cfg.payment_provider_id = provider.id
  and lower(provider.name) = 'stripe'
  and cfg.setting = 'test_public';
