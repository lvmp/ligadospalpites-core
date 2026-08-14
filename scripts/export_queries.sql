-- ==============================================================================
-- QUERIES PARA EXPORTAÇÃO DIRETA VIA NEON CONSOLE (SQL EDITOR)
-- ==============================================================================
-- Execute os comandos abaixo no SQL Editor do Neon (https://console.neon.tech).
-- Copie os resultados gerados e salve em um arquivo chamado: backups/neon_data.sql
-- ==============================================================================

-- 1. Exportar Usuários (tbl_users)
SELECT 'INSERT INTO tbl_users (id, firebase_uid, email, name, created_at) VALUES (' 
  || quote_literal(id) || ', ' 
  || quote_literal(firebase_uid) || ', ' 
  || quote_literal(email) || ', ' 
  || quote_literal(name) || ', ' 
  || quote_literal(created_at) || ') ON CONFLICT (id) DO NOTHING;' AS insert_statement
FROM tbl_users;

-- 2. Exportar Grupos (tbl_groups) - se existir na base
SELECT 'INSERT INTO tbl_groups (id, name, code, owner_id, created_at) VALUES ('
  || quote_literal(id) || ', '
  || quote_literal(name) || ', '
  || quote_literal(code) || ', '
  || quote_literal(owner_id) || ', '
  || quote_literal(created_at) || ') ON CONFLICT (id) DO NOTHING;' AS insert_statement
FROM tbl_groups;

-- 3. Exportar Membros de Grupos (tbl_group_members)
SELECT 'INSERT INTO tbl_group_members (group_id, user_id, joined_at) VALUES ('
  || quote_literal(group_id) || ', '
  || quote_literal(user_id) || ', '
  || quote_literal(joined_at) || ') ON CONFLICT DO NOTHING;' AS insert_statement
FROM tbl_group_members;

-- 4. Exportar Palpites (tbl_predictions)
SELECT 'INSERT INTO tbl_predictions (id, user_id, match_id, home_score, away_score, points_awarded, created_at) VALUES ('
  || quote_literal(id) || ', '
  || quote_literal(user_id) || ', '
  || quote_literal(match_id) || ', '
  || home_score || ', '
  || away_score || ', '
  || COALESCE(points_awarded::text, 'NULL') || ', '
  || quote_literal(created_at) || ') ON CONFLICT (id) DO NOTHING;' AS insert_statement
FROM tbl_predictions;
