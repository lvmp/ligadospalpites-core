-- ==============================================================================
-- Migration V27: Add performance indexes for matches and predictions
-- Eliminates table scans during fixture queries and match finish prediction resolution
-- ==============================================================================

-- 1. Matches indexes for fast retrieval by league and status
CREATE INDEX IF NOT EXISTS idx_matches_league_status ON tbl_matches(league_id, status);
CREATE INDEX IF NOT EXISTS idx_matches_league_id ON tbl_matches(league_id);

-- 2. Predictions index for fast lookup when a match finishes (findByMatchId)
CREATE INDEX IF NOT EXISTS idx_predictions_match_id ON tbl_predictions(match_id);

-- 3. Predictions index for user league summary queries
CREATE INDEX IF NOT EXISTS idx_predictions_league_user ON tbl_predictions(league_id, user_id);

-- 4. Special predictions index by league
CREATE INDEX IF NOT EXISTS idx_special_predictions_league ON tbl_special_predictions(league_id);
