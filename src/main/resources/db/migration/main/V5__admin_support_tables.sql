CREATE TABLE IF NOT EXISTS voucher_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(255),
    discount_type INTEGER NOT NULL DEFAULT 0,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS delivery_types (
    id UUID PRIMARY KEY,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS delivery_rules (
    id UUID PRIMARY KEY,
    delivery_type_id UUID NOT NULL REFERENCES delivery_types(id) ON DELETE CASCADE,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    country VARCHAR(10),
    min_cart_price NUMERIC(18,2),
    max_cart_price NUMERIC(18,2),
    price NUMERIC(18,2) NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS discount_rules (
    id UUID PRIMARY KEY,
    type INTEGER NOT NULL DEFAULT 0,
    name JSONB NOT NULL,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS discount_rule_product (
    discount_rule_id UUID REFERENCES discount_rules(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_rule_id, product_id)
);

CREATE TABLE IF NOT EXISTS discount_rule_product_category (
    discount_rule_id UUID REFERENCES discount_rules(id) ON DELETE CASCADE,
    product_category_id UUID REFERENCES product_categories(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_rule_id, product_category_id)
);

CREATE TABLE IF NOT EXISTS file_contents (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120),
    size BIGINT,
    path VARCHAR(1000),
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS notes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    noteable_type VARCHAR(120),
    noteable_id UUID,
    text TEXT NOT NULL,
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS role_has_permissions (
    role_id BIGINT REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS model_has_roles (
    role_id BIGINT REFERENCES roles(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, user_id)
);

CREATE TABLE IF NOT EXISTS audits (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event VARCHAR(80) NOT NULL,
    auditable_type VARCHAR(120),
    auditable_id UUID,
    old_values JSONB,
    new_values JSONB,
    url VARCHAR(1000),
    ip_address VARCHAR(64),
    user_agent VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO roles(name, created_at, updated_at)
VALUES ('super_admin', now(), now()), ('customer', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO model_has_roles(role_id, user_id)
SELECT r.id, '11111111-1111-1111-1111-111111111111'::uuid
FROM roles r
WHERE r.name = 'super_admin'
ON CONFLICT DO NOTHING;
