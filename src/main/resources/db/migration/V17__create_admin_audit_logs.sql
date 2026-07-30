CREATE TABLE tbl_admin_audit_logs (
    id UUID PRIMARY KEY,
    operator_id VARCHAR(128) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_id VARCHAR(128),
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_admin_audit_logs_timestamp ON tbl_admin_audit_logs(timestamp DESC);
CREATE INDEX idx_admin_audit_logs_action ON tbl_admin_audit_logs(action);
