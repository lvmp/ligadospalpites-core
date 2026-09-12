-- Migration V25: Create tbl_match_events for Live and Historical Match Timeline (Minuto a Minuto)
CREATE TABLE IF NOT EXISTS tbl_match_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES tbl_matches(id) ON DELETE CASCADE,
    minute INTEGER NOT NULL,
    extra_minute INTEGER,
    period VARCHAR(20) NOT NULL DEFAULT '1H', -- '1H', '2H', 'ET', 'PENALTIES', etc.
    event_type VARCHAR(50) NOT NULL, -- 'GOAL', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION', 'PENALTY', 'VAR', 'CHANCE', 'PERIOD_START', 'PERIOD_END', 'INFO'
    team_name VARCHAR(150),
    player_name VARCHAR(150),
    player_assist_name VARCHAR(150),
    description TEXT NOT NULL,
    is_important BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_match_events_match_minute ON tbl_match_events(match_id, minute DESC, created_at DESC);
