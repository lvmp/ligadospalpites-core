CREATE TABLE tbl_user_entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    entitlement_type VARCHAR(50) NOT NULL, -- e.g., PREMIUM, SPORT_PASS
    sport_id UUID, -- NULL if PREMIUM (unlocks all)
    expires_at TIMESTAMP WITH TIME ZONE, -- NULL if lifetime or not expired yet
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_entitlements_user ON tbl_user_entitlements(user_id);
