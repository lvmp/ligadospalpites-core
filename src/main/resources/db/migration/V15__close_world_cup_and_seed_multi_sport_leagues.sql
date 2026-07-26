-- Migration V15: Close World Cup 2026 and seed new multi-sport leagues (Brasileirão, Libertadores, La Liga, Premier, Champions League, NBA, NBB)

-- 1. Deactivate FIFA World Cup 2026 League and Season
UPDATE tbl_leagues
SET is_active = false
WHERE id = 'e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e';

UPDATE tbl_seasons
SET is_active = false
WHERE id = '50c22998-33b2-4d9a-ba02-4be71a1be992';

-- 2. Seed Basquete Sport
INSERT INTO tbl_sports (id, name)
VALUES ('e5284bf1-d576-4740-97cc-f06bca181cb2', 'Basquete')
ON CONFLICT (id) DO NOTHING;

-- 3. Seed New Leagues (5 Football and 2 Basketball)
-- Football Sport ID: 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c'
-- Basketball Sport ID: 'e5284bf1-d576-4740-97cc-f06bca181cb2'

INSERT INTO tbl_leagues (id, name, sport_id, is_active) VALUES
-- Football Leagues
('3dbd8422-9e22-4411-b0db-b06d0421da6a', 'Campeonato Brasileiro', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('4acdf011-fbde-4122-83bc-c46b1ba847de', 'Copa Libertadores', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('9284ca51-bb54-47c1-841f-81ab28120fa2', 'Campeonato Espanhol', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('827d043c-62c2-402c-b011-3ba2849e7b23', 'Campeonato Inglês', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('e2d03a11-b9db-44ab-ba02-411a0c0bcf14', 'UEFA Champions League', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
-- Basketball Leagues
('5c1e3a11-b9db-44ab-ba02-411a0c0bcf14', 'NBA', 'e5284bf1-d576-4740-97cc-f06bca181cb2', true),
('2dbd1112-9cde-4411-b0db-b06d0421da6a', 'NBB', 'e5284bf1-d576-4740-97cc-f06bca181cb2', true)
ON CONFLICT (id) DO NOTHING;

-- 4. Seed Seasons for New Leagues
INSERT INTO tbl_seasons (id, league_id, name, start_date, end_date, is_active, external_season_code) VALUES
-- Brasileirão 2026
('e89c6fb4-2be6-4447-b2e1-87bbca8474ef', '3dbd8422-9e22-4411-b0db-b06d0421da6a', '2026', '2026-04-11 00:00:00+00', '2026-12-06 23:59:59+00', true, 2026),
-- Libertadores 2026
('cf1e4a36-39db-432a-bc9f-1d48c036cf88', '4acdf011-fbde-4122-83bc-c46b1ba847de', '2026', '2026-02-04 00:00:00+00', '2026-11-21 23:59:59+00', true, 2026),
-- Campeonato Espanhol 2026
('bfd06fe2-23c1-419b-a0db-cb6b4021fa42', '9284ca51-bb54-47c1-841f-81ab28120fa2', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
-- Campeonato Inglês 2026
('c830dbf2-ea01-447b-a2ab-8c68ab0d4fe2', '827d043c-62c2-402c-b011-3ba2849e7b23', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
-- UEFA Champions League 2026
('da6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', 'e2d03a11-b9db-44ab-ba02-411a0c0bcf14', '2026/2027', '2026-09-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
-- NBA 2026
('8a6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef', '5c1e3a11-b9db-44ab-ba02-411a0c0bcf14', '2026/2027', '2026-10-20 00:00:00+00', '2027-06-20 23:59:59+00', true, 2026),
-- NBB 2026
('189c6fb4-2be6-4447-b2e1-87bbca8474ef', '2dbd1112-9cde-4411-b0db-b06d0421da6a', '2026/2027', '2026-10-15 00:00:00+00', '2027-06-15 23:59:59+00', true, 2026)
ON CONFLICT (id) DO NOTHING;
