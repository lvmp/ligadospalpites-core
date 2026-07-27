-- Migration V16: Add team logo URLs to matches
ALTER TABLE tbl_matches ADD COLUMN home_team_logo_url VARCHAR(512);
ALTER TABLE tbl_matches ADD COLUMN away_team_logo_url VARCHAR(512);
