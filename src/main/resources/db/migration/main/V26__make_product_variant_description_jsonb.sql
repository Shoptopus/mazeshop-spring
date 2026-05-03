alter table product_variants
    alter column description type jsonb
    using case
        when description is null or btrim(description) = '' then '{}'::jsonb
        when left(btrim(description), 1) = '{' then description::jsonb
        else jsonb_build_object('en', description)
    end;
