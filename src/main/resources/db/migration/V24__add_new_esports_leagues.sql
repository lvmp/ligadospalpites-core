-- Migration V24: Add active eSports leagues (ESL Pro League, BLAST Premier, VCT Champions) and their 2026 active seasons

-- 1. Insert New eSports Leagues
INSERT INTO tbl_leagues (id, name, sport_id, is_active) VALUES
('bc1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'Counter-Strike 2 - ESL Pro League', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true),
('cc1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'Counter-Strike 2 - BLAST Premier', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true),
('dc1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'Valorant - VCT Champions', '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14', true)
ON CONFLICT (id) DO NOTHING;

-- 2. Insert Active Seasons (2026) for New Leagues
INSERT INTO tbl_seasons (id, league_id, name, start_date, end_date, is_active, external_season_code) VALUES
('5d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', 'bc1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-08-01 00:00:00+00', '2026-11-30 23:59:59+00', true, 2026),
('6d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', 'cc1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-07-15 00:00:00+00', '2026-12-20 23:59:59+00', true, 2026),
('7d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', 'dc1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026', '2026-08-01 00:00:00+00', '2026-10-31 23:59:59+00', true, 2026)
ON CONFLICT (id) DO NOTHING;
