-- Migration V11: Create seasons table and integrate with existing matches

CREATE TABLE tbl_seasons (
    id UUID PRIMARY KEY,
    league_id UUID NOT NULL REFERENCES tbl_leagues(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    external_season_code INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Partial unique index to ensure at most one active season per league
CREATE UNIQUE INDEX uq_active_season_per_league ON tbl_seasons(league_id) WHERE (is_active = true);

-- Add season_id column to tbl_matches as temporarily nullable
ALTER TABLE tbl_matches ADD COLUMN season_id UUID REFERENCES tbl_seasons(id);

-- Create index for performance
CREATE INDEX idx_matches_season ON tbl_matches(season_id);

-- Seed initial World Cup 2026 Season
-- Parent League ID: 'e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e'
INSERT INTO tbl_seasons (id, league_id, name, start_date, end_date, is_active, external_season_code)
VALUES (
    '50c22998-33b2-4d9a-ba02-4be71a1be992',
    'e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e',
    '2026',
    '2026-06-01 00:00:00+00',
    '2026-07-31 23:59:59+00',
    true,
    2026
);

-- Update existing matches to reference the new World Cup 2026 season
UPDATE tbl_matches
SET season_id = '50c22998-33b2-4d9a-ba02-4be71a1be992'
WHERE league_id = 'e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e';

-- Make season_id NOT NULL to enforce consistency
ALTER TABLE tbl_matches ALTER COLUMN season_id SET NOT NULL;
