ALTER TABLE tbl_revenuecat_events ADD COLUMN status VARCHAR(50) DEFAULT 'RECEIVED' NOT NULL;
ALTER TABLE tbl_revenuecat_events ADD COLUMN failure_reason VARCHAR(255);
