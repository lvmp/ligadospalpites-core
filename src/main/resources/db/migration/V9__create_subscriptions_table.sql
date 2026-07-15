CREATE TABLE tbl_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    store VARCHAR(50) NOT NULL, -- ex: APPLE_APP_STORE, GOOGLE_PLAY, STRIPE
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    product_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL, -- ex: ACTIVE, CANCELLED, EXPIRED
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tbl_revenuecat_events (
    id VARCHAR(100) PRIMARY KEY, -- event.id vindo do RevenueCat
    type VARCHAR(100) NOT NULL, -- event.type (ex: INITIAL_PURCHASE)
    app_user_id VARCHAR(128) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL, -- JSON bruto completo do evento para auditoria
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_subscriptions_user ON tbl_subscriptions(user_id);
