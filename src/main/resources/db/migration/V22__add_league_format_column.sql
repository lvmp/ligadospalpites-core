-- Migration V22: Add format column to tbl_leagues and set tournament formats

ALTER TABLE tbl_leagues
ADD COLUMN IF NOT EXISTS format VARCHAR(50) NOT NULL DEFAULT 'POINTS';

-- Set Copa Libertadores, Champions League, Eurocopa, Worlds and CS2 Major format to GROUPS_AND_KNOCKOUT
UPDATE tbl_leagues
SET format = 'GROUPS_AND_KNOCKOUT'
WHERE id IN (
  '4acdf011-fbde-4122-83bc-c46b1ba847de', -- Copa Libertadores
  'e2d03a11-b9db-44ab-ba02-411a0c0bcf14', -- UEFA Champions League
  '6acdf011-fbde-4122-83bc-c46b1ba847de', -- Eurocopa
  '9c1e3a11-b9db-44ab-ba02-411a0c0bcf14', -- CS2 Major
  'ac1e3a11-b9db-44ab-ba02-411a0c0bcf14'  -- LoL Worlds
);

-- Set Copa do Brasil format to KNOCKOUT
UPDATE tbl_leagues
SET format = 'KNOCKOUT'
WHERE id IN (
  'b3cdf011-fbde-4122-83bc-c46b1ba847de'  -- Copa do Brasil
);
