# Instruções de Migração: Neon ➔ Supabase

Este diretório contém os scripts reutilizáveis para backup (dump) do banco de dados **Neon PostgreSQL** e restauração (restore) no **Supabase PostgreSQL**.

---

## 🛠️ Pré-requisitos

É necessário ter os utilitários cliente do PostgreSQL (`pg_dump` e `pg_restore`) instalados no PATH do sistema.

- **Windows**: Instalado junto com o PostgreSQL ou via Chocolatey (`choco install postgresql16`), ou incluído no pgAdmin.
- **Linux / Ubuntu**: `sudo apt-get install postgresql-client`
- **macOS**: `brew install postgresql`

---

## 🔐 Strings de Conexão (Formatos Aceitos)

### 1. Neon PostgreSQL (Origem)
Convertendo de formato JDBC para PostgreSQL URL padrão:
- **Formato JDBC original**: `jdbc:postgresql://ep-super-king-aws3va5l-pooler.c-12.us-east-1.aws.neon.tech/neondb?sslmode=require`
- **Formato Postgres Standard**: `postgresql://neondb_owner:[SUA-SENHA-NEON]@ep-super-king-aws3va5l-pooler.c-12.us-east-1.aws.neon.tech/neondb?sslmode=require`

### 2. Supabase PostgreSQL (Destino)
- **Formato Direct Connection (Porta 5432)**: `postgresql://postgres:[SUA-SENHA-SUPABASE]@db.cuagefxprkgoqkjpjqeo.supabase.co:5432/postgres`

---

## 🚀 Como Executar

### No Windows (PowerShell)

```powershell
.\scripts\migrate_neon_to_supabase.ps1 `
    -NeonUrl "postgresql://neondb_owner:SENHA_NEON@ep-super-king-aws3va5l-pooler.c-12.us-east-1.aws.neon.tech/neondb?sslmode=require" `
    -SupabaseUrl "postgresql://postgres:SENHA_SUPABASE@db.cuagefxprkgoqkjpjqeo.supabase.co:5432/postgres"
```

> **Nota:** Se você não passar os parâmetros `-NeonUrl` ou `-SupabaseUrl`, o script solicitará as senhas no terminal de forma mascarada/segura.

### No Linux / macOS / WSL (Bash)

```bash
chmod +x ./scripts/migrate_neon_to_supabase.sh

NEON_DB_URL="postgresql://neondb_owner:SENHA_NEON@ep-super-king-aws3va5l-pooler.c-12.us-east-1.aws.neon.tech/neondb?sslmode=require" \
SUPABASE_DB_URL="postgresql://postgres:SENHA_SUPABASE@db.cuagefxprkgoqkjpjqeo.supabase.co:5432/postgres" \
./scripts/migrate_neon_to_supabase.sh
```

---

## 📌 Cuidados e Boas Práticas

1. **Permissões e Roles**:
   O script inclui `--no-owner` e `--no-privileges` para evitar falhas de permissão com a role `neon_superuser` no Supabase.
2. **Schema `public`**:
   O dump e restore são focados no schema `public` por padrão para não interferir nas tabelas dos produtos nativos do Supabase (`auth`, `storage`, `realtime`).
