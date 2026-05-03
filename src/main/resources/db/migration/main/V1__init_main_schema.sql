CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255),
    name VARCHAR(100),
    initials VARCHAR(5),
    prefix VARCHAR(5),
    phone VARCHAR(50),
    avatar JSONB,
    email_verified_at TIMESTAMPTZ,
    client_ref VARCHAR(12),
    temporary BOOLEAN NOT NULL DEFAULT FALSE,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    slug VARCHAR(255) NOT NULL,
    last_seen TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS access_tokens (
    id UUID PRIMARY KEY,
    type INTEGER NOT NULL DEFAULT 0,
    token VARCHAR(120) NOT NULL,
    accessable_type VARCHAR(120) NOT NULL,
    accessable_id VARCHAR(120) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    issuer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    expiry TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_categories (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES product_categories(id) ON DELETE SET NULL,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description JSONB NOT NULL,
    menu_image JSONB,
    header_image JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_tags (
    id UUID PRIMARY KEY,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description JSONB,
    badge JSONB,
    display_badge BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_attributes (
    id UUID PRIMARY KEY,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    type INTEGER NOT NULL DEFAULT 0,
    image JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_attribute_options (
    id UUID PRIMARY KEY,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    value VARCHAR(255) NOT NULL,
    product_attribute_id UUID REFERENCES product_attributes(id),
    image JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    name JSONB NOT NULL,
    slug VARCHAR(255) NOT NULL,
    short_description JSONB NOT NULL,
    description JSONB NOT NULL,
    price NUMERIC(18,2) NOT NULL,
    headline JSONB,
    subtitle JSONB,
    status INTEGER NOT NULL DEFAULT 0,
    purchase_count BIGINT NOT NULL DEFAULT 0,
    stock BIGINT NOT NULL DEFAULT 0,
    backup_stock BIGINT,
    sku VARCHAR(50) UNIQUE,
    virtual BOOLEAN NOT NULL DEFAULT FALSE,
    weight INTEGER,
    cover_photo JSONB,
    available_attribute_options JSONB,
    rating DOUBLE PRECISION,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin ((coalesce(name->>'en', '')) gin_trgm_ops);

CREATE TABLE IF NOT EXISTS product_variants (
    id UUID PRIMARY KEY,
    slug VARCHAR(255) NOT NULL,
    price NUMERIC(18,2) NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    data TEXT,
    stock BIGINT NOT NULL DEFAULT 0,
    sku VARCHAR(50) UNIQUE,
    description TEXT,
    attribute_options JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_product_category (
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    product_category_id UUID REFERENCES product_categories(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, product_category_id)
);

CREATE TABLE IF NOT EXISTS product_product_tag (
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    product_tag_id UUID REFERENCES product_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, product_tag_id)
);

CREATE TABLE IF NOT EXISTS carts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ip_address VARCHAR(100),
    slug VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS cart_product (
    id UUID PRIMARY KEY,
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    product_variant_id UUID REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(cart_id, product_id, product_variant_id)
);

CREATE TABLE IF NOT EXISTS addresses (
    id UUID PRIMARY KEY,
    address_line_1 VARCHAR(255) NOT NULL,
    town VARCHAR(255) NOT NULL,
    post_code VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    country VARCHAR(255) NOT NULL DEFAULT 'UK',
    name VARCHAR(255),
    address_line_2 VARCHAR(255),
    lat VARCHAR(255),
    lon VARCHAR(255),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    address_id UUID NOT NULL REFERENCES addresses(id),
    user_id UUID REFERENCES users(id),
    delivery_type_id UUID,
    voucher_code_id UUID,
    original_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_discount NUMERIC(18,2) NOT NULL DEFAULT 0,
    delivery_cost NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'GBP',
    status INTEGER NOT NULL DEFAULT 0,
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_product (
    id UUID PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    product_variant_id UUID REFERENCES product_variants(id) ON DELETE SET NULL,
    amount INTEGER NOT NULL DEFAULT 1,
    name TEXT NOT NULL,
    original_unit_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    unit_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    full_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    final_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    unit_discount NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_discount NUMERIC(18,2) NOT NULL DEFAULT 0,
    returned BOOLEAN NOT NULL DEFAULT FALSE,
    returned_at TIMESTAMPTZ,
    return_reason VARCHAR(255),
    slug VARCHAR(255) NOT NULL,
    urls JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id, product_id, product_variant_id)
);

CREATE TABLE IF NOT EXISTS payment_sources (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    source_id VARCHAR(255),
    exp_month VARCHAR(20),
    exp_year VARCHAR(20),
    last_four VARCHAR(10),
    brand VARCHAR(120),
    stripe_user_id VARCHAR(255),
    payment_method_id INTEGER NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    payable_type VARCHAR(255) NOT NULL,
    payable_id VARCHAR(255) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    payment_source_id UUID REFERENCES payment_sources(id) ON DELETE SET NULL,
    proof JSONB,
    status INTEGER NOT NULL DEFAULT 0,
    payment_ref VARCHAR(150),
    method_ref VARCHAR(150),
    type INTEGER NOT NULL,
    description VARCHAR(250),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_providers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    test_mode BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_provider_configs (
    id BIGSERIAL PRIMARY KEY,
    payment_provider_id BIGINT NOT NULL REFERENCES payment_providers(id) ON DELETE CASCADE,
    setting VARCHAR(255) NOT NULL,
    value VARCHAR(1000),
    test_value VARCHAR(1000),
    public_value BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
