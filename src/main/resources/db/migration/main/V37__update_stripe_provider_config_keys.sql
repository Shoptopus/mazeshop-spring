update payment_provider_configs
set setting = 'publishable_key', updated_at = now()
where setting = 'public_key'
  and payment_provider_id in (
    select id from payment_providers where lower(name) = 'stripe'
  );

insert into payment_provider_configs(payment_provider_id, setting, value, test_value, public_value, created_at, updated_at)
select id, 'secret_key', null, null, false, now(), now()
from payment_providers
where lower(name) = 'stripe'
  and not exists (
    select 1
    from payment_provider_configs cfg
    where cfg.payment_provider_id = payment_providers.id
      and cfg.setting = 'secret_key'
  );
