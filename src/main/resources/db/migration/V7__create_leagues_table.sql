CREATE TABLE tbl_sports (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tbl_leagues (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    sport_id UUID NOT NULL REFERENCES tbl_sports(id),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_leagues_active ON tbl_leagues(is_active);

-- Seed initial Sports
INSERT INTO tbl_sports (id, name)
VALUES ('f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', 'Futebol');

-- Seed initial active leagues (Copa do Mundo de Futebol)
INSERT INTO tbl_leagues (id, name, sport_id, is_active)
VALUES ('e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e', 'Copa do Mundo', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true);
