#!/usr/bin/env bash
# ==============================================================================
# Script de Backup do Neon PostgreSQL e Restore no Supabase PostgreSQL
# ==============================================================================
set -euo pipefail

# Cores para saída no terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_err()   { echo -e "${RED}[ERROR]${NC} $1"; }

# 1. Verificar utilitários do PostgreSQL
if ! command -v pg_dump &> /dev/null; then
    log_err "Utilitário 'pg_dump' não foi encontrado no PATH. Instale o PostgreSQL client tools."
    exit 1
fi

if ! command -v pg_restore &> /dev/null; then
    log_err "Utilitário 'pg_restore' não foi encontrado no PATH. Instale o PostgreSQL client tools."
    exit 1
fi

if ! command -v psql &> /dev/null; then
    log_err "Utilitário 'psql' não foi encontrado no PATH. Instale o PostgreSQL client tools."
    exit 1
fi

# 2. Obter Connection Strings
NEON_URL="${NEON_DB_URL:-}"
SUPABASE_URL="${SUPABASE_DB_URL:-}"

if [ -z "$NEON_URL" ]; then
    echo -n "Digite a Connection String do Neon: "
    read -r -s NEON_URL
    echo ""
fi

if [ -z "$SUPABASE_URL" ]; then
    echo -n "Digite a Connection String do Supabase: "
    read -r -s SUPABASE_URL
    echo ""
fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"

DUMP_FILE="${BACKUP_DIR}/neon_backup_${TIMESTAMP}.dump"
SCHEMA_NAME="${DB_SCHEMA:-public}"

log_info "Iniciando processo de migração..."
log_info "Schema-alvo: ${SCHEMA_NAME}"
log_info "Arquivo de Backup: ${DUMP_FILE}"

# 3. Executar pg_dump do Neon
log_info "Passo 1/2: Efetuando pg_dump da base Neon..."
if pg_dump \
    --dbname="$NEON_URL" \
    --format=custom \
    --schema="$SCHEMA_NAME" \
    --no-owner \
    --no-privileges \
    --verbose \
    --file="$DUMP_FILE"; then
    log_ok "Dump do Neon concluído com sucesso: ${DUMP_FILE}"
else
    log_err "Falha ao gerar o dump do Neon."
    exit 1
fi

# 4. Executar pg_restore no Supabase
log_info "Passo 2/2: Restaurando dados no Supabase..."
if pg_restore \
    --dbname="$SUPABASE_URL" \
    --schema="$SCHEMA_NAME" \
    --no-owner \
    --no-privileges \
    --clean \
    --if-exists \
    --verbose \
    "$DUMP_FILE"; then
    log_ok "Restauração no Supabase concluída com sucesso!"
else
    log_warn "pg_restore finalizou com avisos (comum em extensões existentes ou tabelas já limpas)."
fi

log_ok "Processo de migração finalizado."
