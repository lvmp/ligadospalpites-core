CREATE TABLE tbl_matches (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL,
    league_id UUID NOT NULL,
    home_team_name VARCHAR(150) NOT NULL,
    away_team_name VARCHAR(150) NOT NULL,
    kickoff_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL, -- e.g., SCHEDULED, LIVE, FINISHED, CANCELLED
    home_score INTEGER,
    away_score INTEGER,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_matches_status ON tbl_matches(status);
CREATE INDEX idx_matches_kickoff ON tbl_matches(kickoff_time);
