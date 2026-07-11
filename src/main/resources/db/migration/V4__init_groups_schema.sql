CREATE TABLE tbl_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    creator_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    scoring_rules_json VARCHAR(1000) NOT NULL, -- Config rules JSON as string
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tbl_group_members (
    group_id UUID NOT NULL REFERENCES tbl_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accumulated_points INTEGER DEFAULT 0 NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_group_members_points ON tbl_group_members(group_id, accumulated_points DESC);
