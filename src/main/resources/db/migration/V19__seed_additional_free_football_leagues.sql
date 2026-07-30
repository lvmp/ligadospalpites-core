-- Migration V19: Seed Additional Free Football Leagues (Ligue 1, Bundesliga, Serie A, Eredivisie, Primeira Liga, Championship, Eurocopa, Copa do Brasil) and Active Seasons

INSERT INTO tbl_leagues (id, name, sport_id, is_active) VALUES
('7acdf011-fbde-4122-83bc-c46b1ba847de', 'Ligue 1', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('8acdf011-fbde-4122-83bc-c46b1ba847de', 'Bundesliga', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('9acdf011-fbde-4122-83bc-c46b1ba847de', 'Serie A Italiana', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('aacdf011-fbde-4122-83bc-c46b1ba847de', 'Eredivisie', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('bacdf011-fbde-4122-83bc-c46b1ba847de', 'Primeira Liga', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('5acdf011-fbde-4122-83bc-c46b1ba847de', 'Championship', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('6acdf011-fbde-4122-83bc-c46b1ba847de', 'Eurocopa', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('b3cdf011-fbde-4122-83bc-c46b1ba847de', 'Copa do Brasil', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tbl_seasons (id, league_id, name, start_date, end_date, is_active, external_season_code) VALUES
('789c6fb4-2be6-4447-b2e1-87bbca8474e1', '7acdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('889c6fb4-2be6-4447-b2e1-87bbca8474e2', '8acdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('989c6fb4-2be6-4447-b2e1-87bbca8474e3', '9acdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('a89c6fb4-2be6-4447-b2e1-87bbca8474e4', 'aacdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('b89c6fb4-2be6-4447-b2e1-87bbca8474e5', 'bacdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('589c6fb4-2be6-4447-b2e1-87bbca8474e6', '5acdf011-fbde-4122-83bc-c46b1ba847de', '2026/2027', '2026-08-15 00:00:00+00', '2027-05-31 23:59:59+00', true, 2026),
('689c6fb4-2be6-4447-b2e1-87bbca8474e7', '6acdf011-fbde-4122-83bc-c46b1ba847de', '2026', '2026-06-01 00:00:00+00', '2026-07-15 23:59:59+00', true, 2026),
('c89c6fb4-2be6-4447-b2e1-87bbca8474e8', 'b3cdf011-fbde-4122-83bc-c46b1ba847de', '2026', '2026-02-01 00:00:00+00', '2026-11-30 23:59:59+00', true, 2026)
ON CONFLICT (id) DO NOTHING;
