-- Migration V16: Add team logo URLs to matches
ALTER TABLE tbl_matches ADD COLUMN IF NOT EXISTS home_team_logo_url VARCHAR(512);
ALTER TABLE tbl_matches ADD COLUMN IF NOT EXISTS away_team_logo_url VARCHAR(512);
