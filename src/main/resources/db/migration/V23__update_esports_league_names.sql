-- Migration V23: Update eSports league names to include full game titles for better UI display

UPDATE tbl_leagues
SET name = 'League of Legends - CBLOL'
WHERE id = '7c1e3a11-b9db-44ab-ba02-411a0c0bcf14';

UPDATE tbl_leagues
SET name = 'Valorant - VCT Americas'
WHERE id = '8c1e3a11-b9db-44ab-ba02-411a0c0bcf14';

UPDATE tbl_leagues
SET name = 'Counter-Strike 2 - Major'
WHERE id = '9c1e3a11-b9db-44ab-ba02-411a0c0bcf14';

UPDATE tbl_leagues
SET name = 'League of Legends - Worlds'
WHERE id = 'ac1e3a11-b9db-44ab-ba02-411a0c0bcf14';
