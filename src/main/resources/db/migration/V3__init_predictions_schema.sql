CREATE TABLE tbl_predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    match_id UUID NOT NULL REFERENCES tbl_matches(id) ON DELETE CASCADE,
    league_id UUID NOT NULL,
    predicted_home_score INTEGER NOT NULL,
    predicted_away_score INTEGER NOT NULL,
    points_awarded INTEGER DEFAULT 0 NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE,
    is_processed BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_match_prediction UNIQUE (user_id, match_id)
);
CREATE INDEX idx_predictions_unprocessed ON tbl_predictions(is_processed) WHERE is_processed = FALSE;

CREATE TABLE tbl_special_predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    league_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL, -- e.g., CHAMPION, TOP_SCORER
    prediction_value VARCHAR(150) NOT NULL, -- e.g., team name or player id
    points_awarded INTEGER DEFAULT 0 NOT NULL,
    is_processed BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_special UNIQUE (user_id, league_id, type)
);
