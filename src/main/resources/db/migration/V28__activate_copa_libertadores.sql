-- Migration V28: Ensure Copa Libertadores and its active season are enabled with correct format
UPDATE tbl_leagues
SET is_active = true,
    format = 'GROUPS_AND_KNOCKOUT'
WHERE id = '4acdf011-fbde-4122-83bc-c46b1ba847de';

UPDATE tbl_seasons
SET is_active = true
WHERE league_id = '4acdf011-fbde-4122-83bc-c46b1ba847de' AND name = '2026';
