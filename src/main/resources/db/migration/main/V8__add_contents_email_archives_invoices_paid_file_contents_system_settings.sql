CREATE TABLE IF NOT EXISTS contents (
    id UUID PRIMARY KEY,
    contentable_type VARCHAR(255),
    contentable_id UUID,
    language_id BIGINT NOT NULL,
    type INTEGER NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contents_language_id ON contents(language_id);
CREATE INDEX IF NOT EXISTS idx_contents_contentable ON contents(contentable_type, contentable_id);

CREATE TABLE IF NOT EXISTS email_archives (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    template_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    address JSONB NOT NULL,
    payment JSONB NOT NULL,
    products JSONB NOT NULL,
    voucher_code JSONB,
    delivery_type JSONB,
    totals JSONB NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    slug VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_order_id ON invoices(order_id);

CREATE TABLE IF NOT EXISTS paid_file_contents (
    id UUID PRIMARY KEY,
    url VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    fileable_type VARCHAR(255) NOT NULL,
    fileable_id UUID NOT NULL,
    title TEXT,
    description TEXT,
    size VARCHAR(50),
    type INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paid_file_contents_fileable ON paid_file_contents(fileable_type, fileable_id);

CREATE TABLE IF NOT EXISTS system_settings (
    id UUID PRIMARY KEY,
    key VARCHAR(60) NOT NULL,
    value TEXT NOT NULL,
    type INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_system_settings_key ON system_settings(key);
