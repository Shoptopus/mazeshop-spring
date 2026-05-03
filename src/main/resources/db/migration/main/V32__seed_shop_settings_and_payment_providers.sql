insert into permissions(name, created_at, updated_at) values
    ('shop_settings.view', now(), now()),
    ('shop_settings.manage', now(), now())
on conflict (name) do nothing;

insert into payment_providers(name, enabled, test_mode, created_at, updated_at)
values
    ('stripe', true, true, now(), now()),
    ('paypal', false, true, now(), now()),
    ('google_pay', false, true, now(), now()),
    ('apple_pay', false, true, now(), now()),
    ('amazon_pay', false, true, now(), now())
on conflict (name) do nothing;
