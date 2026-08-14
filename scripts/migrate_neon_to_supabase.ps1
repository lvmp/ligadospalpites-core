<#
.SYNOPSIS
    Script PowerShell para migração/backup de banco de dados do Neon PostgreSQL para Supabase PostgreSQL.
.DESCRIPTION
    Realiza o dump do banco Neon PostgreSQL via pg_dump e o restore no Supabase via pg_restore (usando pg_dump local ou o container Docker ligadospalpites-postgres).
.PARAMETER NeonUrl
    Connection string do banco de dados Neon.
.PARAMETER SupabaseUrl
    Connection string do banco de dados Supabase.
.PARAMETER Schema
    Nome do schema a ser migrado (padrão: public).
.PARAMETER BackupDir
    Diretório local para armazenar o arquivo .dump.
.PARAMETER ContainerName
    Nome do container Docker com Postgres instalado (padrão: ligadospalpites-postgres).
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory=$false)]
    [string]$NeonUrl = $env:NEON_DB_URL,

    [Parameter(Mandatory=$false)]
    [string]$SupabaseUrl = $env:SUPABASE_DB_URL,

    [Parameter(Mandatory=$false)]
    [string]$Schema = "public",

    [Parameter(Mandatory=$false)]
    [string]$BackupDir = ".\backups",

    [Parameter(Mandatory=$false)]
    [string]$ContainerName = "ligadospalpites-postgres"
)

$ErrorActionPreference = "Stop"

function Write-Info ($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success ($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn ($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-ErrorMsg ($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Ensure backup dir
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dumpFileName = "neon_backup_$timestamp.dump"
$dumpFilePath = Join-Path -Path (Resolve-Path $BackupDir) -ChildPath $dumpFileName

# Verificar se pg_dump nativo está disponível ou se usaremos o Docker container
$pgDumpCmd = Get-Command "pg_dump" -ErrorAction SilentlyContinue
$useDocker = $false

if (-not $pgDumpCmd) {
    # Testar se o container Docker está rodando
    $dockerCheck = docker ps --filter "name=$ContainerName" --format "{{.Names}}" 2>$null
    if ($dockerCheck -eq $ContainerName) {
        Write-Info "Utilitário 'pg_dump' local não encontrado, utilizando o container Docker '$ContainerName'..."
        $useDocker = $true
    } else {
        Write-ErrorMsg "Nem o 'pg_dump.exe' local nem o container Docker '$ContainerName' foram encontrados rodando."
        exit 1
    }
}

# Solicitar URLs se não fornecidas
if ([string]::IsNullOrWhiteSpace($NeonUrl)) {
    $secureNeon = Read-Host "Digite a Connection String do Neon" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNeon)
    $NeonUrl = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

if ([string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $secureSupa = Read-Host "Digite a Connection String do Supabase" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSupa)
    $SupabaseUrl = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

Write-Info "Iniciando migração Neon -> Supabase"
Write-Info "Schema selecionado: $Schema"
Write-Info "Destino do Backup: $dumpFilePath"

# 1. Gerar Dump do Neon
Write-Info "Passo 1/2: Gerando dump da base Neon via pg_dump..."

if ($useDocker) {
    $containerDumpPath = "/tmp/$dumpFileName"
    docker exec $ContainerName pg_dump --dbname="$NeonUrl" --format=custom --schema="$Schema" --no-owner --no-privileges --verbose --file="$containerDumpPath"
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Falha ao executar pg_dump dentro do container Docker."
        exit 1
    }
    docker cp "${ContainerName}:${containerDumpPath}" $dumpFilePath
    docker exec $ContainerName rm -f $containerDumpPath
} else {
    & pg_dump --dbname="$NeonUrl" --format=custom --schema="$Schema" --no-owner --no-privileges --verbose --file="$dumpFilePath"
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Falha ao executar pg_dump localmente."
        exit 1
    }
}
Write-Success "Dump do Neon salvo com sucesso em: $dumpFilePath"

# 2. Restaurar no Supabase
Write-Info "Passo 2/2: Restaurando dados no Supabase via pg_restore..."
if ($useDocker) {
    $containerRestorePath = "/tmp/$dumpFileName"
    docker cp $dumpFilePath "${ContainerName}:${containerRestorePath}"
    docker exec $ContainerName pg_restore --dbname="$SupabaseUrl" --schema="$Schema" --no-owner --no-privileges --clean --if-exists --verbose $containerRestorePath
    $restoreCode = $LASTEXITCODE
    docker exec $ContainerName rm -f $containerRestorePath
} else {
    & pg_restore --dbname="$SupabaseUrl" --schema="$Schema" --no-owner --no-privileges --clean --if-exists --verbose "$dumpFilePath"
    $restoreCode = $LASTEXITCODE
}

if ($restoreCode -eq 0) {
    Write-Success "Restauração finalizada com sucesso!"
} else {
    Write-Warn "pg_restore finalizou com código $restoreCode (avisos de extensões/schemas já existentes são normais)."
}

Write-Success "Processo de migração concluído!"
