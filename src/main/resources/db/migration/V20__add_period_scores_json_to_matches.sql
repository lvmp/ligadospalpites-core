-- Migration V20: Add period_scores_json column to tbl_matches for storing quarter and period breakdowns in JSONB format
ALTER TABLE tbl_matches ADD COLUMN IF NOT EXISTS period_scores_json JSONB;
