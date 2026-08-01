-- Migration V21: eSports schema extensions (number_of_games, stream_url), Riot profiles table and eSports seeding

-- 1. Extend tbl_matches with eSports attributes
ALTER TABLE tbl_matches ADD COLUMN IF NOT EXISTS number_of_games INT DEFAULT 1;
ALTER TABLE tbl_matches ADD COLUMN IF NOT EXISTS stream_url VARCHAR(512);

-- 2. Create tbl_user_riot_profiles
CREATE TABLE IF NOT EXISTS tbl_user_riot_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    puuid VARCHAR(255) NOT NULL UNIQUE,
    game_name VARCHAR(100) NOT NULL,
    tag_line VARCHAR(50) NOT NULL,
    lol_rank VARCHAR(50),
    valorant_rank VARCHAR(50),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_riot_profiles_user_id ON tbl_user_riot_profiles(user_id);

-- 3. Seed eSports Sport
INSERT INTO tbl_sports (id, name)
VALUES ('9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'eSports')
ON CONFLICT (id) DO NOTHING;

-- 4. Seed eSports Leagues
INSERT INTO tbl_leagues (id, name, sport_id, is_active) VALUES
('7c1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'CBLOL', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true),
('8c1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'VCT Americas', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true),
('9c1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'CS2 Major', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true),
('ac1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'Worlds', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true)
ON CONFLICT (id) DO NOTHING;

-- 5. Seed eSports Active Seasons
INSERT INTO tbl_seasons (id, league_id, name, start_date, end_date, is_active, external_season_code) VALUES
('1d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', '7c1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-01-15 00:00:00+00', '2026-10-31 23:59:59+00', true, 2026),
('2d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', '8c1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-02-01 00:00:00+00', '2026-09-30 23:59:59+00', true, 2026),
('3d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', '9c1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-03-01 00:00:00+00', '2026-12-15 23:59:59+00', true, 2026),
('4d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', 'ac1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-09-15 00:00:00+00', '2026-11-15 23:59:59+00', true, 2026)
ON CONFLICT (id) DO NOTHING;
