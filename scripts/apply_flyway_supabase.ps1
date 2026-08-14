<#
.SYNOPSIS
    Script PowerShell para aplicar sequencialmente todas as migrações Flyway (V1..V23) no Supabase.
.PARAMETER SupabaseUrl
    Connection string do banco de dados Supabase.
.PARAMETER MigrationDir
    Diretório contendo os arquivos .sql de migração (padrão: src/main/resources/db/migration).
.PARAMETER ContainerName
    Nome do container Docker com Postgres (padrão: ligadospalpites-postgres).
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory=$false)]
    [string]$SupabaseUrl = $env:SUPABASE_DB_URL,

    [Parameter(Mandatory=$false)]
    [string]$MigrationDir = ".\src\main\resources\db\migration",

    [Parameter(Mandatory=$false)]
    [string]$ContainerName = "ligadospalpites-postgres"
)

$ErrorActionPreference = "Stop"

function Write-Info ($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success ($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-ErrorMsg ($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

if ([string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $secureSupa = Read-Host "Digite a Connection String do Supabase" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSupa)
    $SupabaseUrl = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

if (-not (Test-Path $MigrationDir)) {
    Write-ErrorMsg "Diretório de migrações não encontrado: $MigrationDir"
    exit 1
}

# Obter arquivos SQL ordenados pelo número de versão (V1, V2, ..., V23)
$migrationFiles = Get-ChildItem -Path $MigrationDir -Filter "*.sql" | Sort-Object { 
    if ($_.Name -match '^V(\d+)__') { [int]$Matches[1] } else { 999 }
}

Write-Info "Encontradas $($migrationFiles.Count) migrações em $MigrationDir."
Write-Info "Conectando ao Supabase e aplicando migrações..."

# Combinar todas as migrações em um único arquivo consolidado
$combinedSqlPath = Join-Path -Path $env:TEMP -ChildPath "supabase_full_migration.sql"
Remove-Item -Path $combinedSqlPath -ErrorAction SilentlyContinue

$combinedContent = @()
$combinedContent += "-- =============================================================================="
$combinedContent += "-- MIGRACAO COMPLETA SUPABASE - LIGA DOS PALPITES"
$combinedContent += "-- Data: $(Get-Date)"
$combinedContent += "-- =============================================================================="
$combinedContent += ""

foreach ($file in $migrationFiles) {
    $combinedContent += "-- ------------------------------------------------------------------------------"
    $combinedContent += "-- Executando: $($file.Name)"
    $combinedContent += "-- ------------------------------------------------------------------------------"
    $combinedContent += Get-Content -Path $file.FullName -Raw
    $combinedContent += ""
}

Set-Content -Path $combinedSqlPath -Value ($combinedContent -join "`n") -Encoding UTF8
Write-Success "Arquivo unificado de migrações gerado em: $combinedSqlPath"

# Copiar para dentro do container Docker para execução via psql
$containerSqlPath = "/tmp/supabase_full_migration.sql"
docker cp $combinedSqlPath "${ContainerName}:${containerSqlPath}"

Write-Info "Executando psql no container Docker '$ContainerName'..."
docker exec $ContainerName psql "$SupabaseUrl" -f $containerSqlPath
$exitCode = $LASTEXITCODE

docker exec $ContainerName rm -f $containerSqlPath

if ($exitCode -eq 0) {
    Write-Success "Todas as 23 migrações Flyway foram aplicadas com sucesso no Supabase!"
} else {
    Write-ErrorMsg "Falha ao aplicar migrações. Verifique o log de saída acima."
    exit 1
}
