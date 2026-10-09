-- Migration V26: Cleanup stale, out-of-season, and invalid eSports matches
-- Sport ID eSports: '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'

-- 1. Delete eSports matches strictly out of the 2026 season or from past splits/years
DELETE FROM tbl_matches
WHERE sport_id = '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'
  AND (
      kickoff_time < '2026-01-01 00:00:00+00'
      OR phase ILIKE '%2024%'
      OR phase ILIKE '%2025%'
      OR home_team_logo_url ILIKE '%dicebear%'
      OR away_team_logo_url ILIKE '%dicebear%'
  );

-- 2. Clean up eSports matches marked FINISHED with 0x0 score (impossible in eSports)
-- If no predictions exist, delete; if predictions exist, convert to CANCELLED.
DELETE FROM tbl_matches
WHERE sport_id = '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'
  AND status = 'FINISHED'
  AND COALESCE(home_score, 0) = 0
  AND COALESCE(away_score, 0) = 0
  AND NOT EXISTS (
      SELECT 1 FROM tbl_predictions p WHERE p.match_id = tbl_matches.id
  );

UPDATE tbl_matches
SET status = 'CANCELLED',
    home_score = NULL,
    away_score = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE sport_id = '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'
  AND status = 'FINISHED'
  AND COALESCE(home_score, 0) = 0
  AND COALESCE(away_score, 0) = 0;

-- 3. Clean up abandoned SCHEDULED matches in the past (kickoff was over 7 days ago and never started)
DELETE FROM tbl_matches
WHERE sport_id = '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'
  AND status = 'SCHEDULED'
  AND kickoff_time < CURRENT_TIMESTAMP - INTERVAL '7 days'
  AND NOT EXISTS (
      SELECT 1 FROM tbl_predictions p WHERE p.match_id = tbl_matches.id
  );

UPDATE tbl_matches
SET status = 'CANCELLED',
    updated_at = CURRENT_TIMESTAMP
WHERE sport_id = '9b1e3a11-b9db-44ab-ba02-411a0c0bcf14'
  AND status = 'SCHEDULED'
  AND kickoff_time < CURRENT_TIMESTAMP - INTERVAL '1 day';
