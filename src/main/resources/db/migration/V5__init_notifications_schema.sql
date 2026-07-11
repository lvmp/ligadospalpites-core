CREATE TABLE tbl_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL UNIQUE,
    fcm_token VARCHAR(255) NOT NULL,
    device_type VARCHAR(20) NOT NULL, -- ANDROID, IOS
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_devices_user ON tbl_devices(user_id);
CREATE INDEX idx_devices_fcm_token ON tbl_devices(fcm_token);

CREATE TABLE tbl_in_app_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_in_app_notifications_user_unread ON tbl_in_app_notifications(user_id, is_read) WHERE is_read = FALSE;
