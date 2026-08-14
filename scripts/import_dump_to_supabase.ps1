<#
.SYNOPSIS
    Script para importar um arquivo .sql de dados (usuários, palpites, etc.) no Supabase PostgreSQL.
.PARAMETER SqlFile
    Caminho para o arquivo .sql a ser importado.
.PARAMETER SupabaseUrl
    Connection string do banco Supabase.
.PARAMETER ContainerName
    Nome do container Docker com Postgres (padrão: ligadospalpites-postgres).
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$SqlFile,

    [Parameter(Mandatory=$false)]
    [string]$SupabaseUrl = $env:SUPABASE_DB_URL,

    [Parameter(Mandatory=$false)]
    [string]$ContainerName = "ligadospalpites-postgres"
)

$ErrorActionPreference = "Stop"

function Write-Info ($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success ($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-ErrorMsg ($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

if (-not (Test-Path $SqlFile)) {
    Write-ErrorMsg "Arquivo SQL não encontrado: $SqlFile"
    exit 1
}

$fullSqlPath = Resolve-Path $SqlFile

if ([string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $secureSupa = Read-Host "Digite a Connection String do Supabase (ex: postgresql://postgres:SENHA@aws-0-us-east-1.pooler.supabase.com:6543/postgres)" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSupa)
    $SupabaseUrl = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

Write-Info "Importando arquivo SQL no Supabase: $fullSqlPath"

# Copiar arquivo para dentro do container
$targetContainerPath = "/tmp/import_data.sql"
docker cp $fullSqlPath "${ContainerName}:${targetContainerPath}"

# Executar psql dentro do container
docker exec $ContainerName psql "$SupabaseUrl" -f $targetContainerPath
$exitCode = $LASTEXITCODE

docker exec $ContainerName rm -f $targetContainerPath

if ($exitCode -eq 0) {
    Write-Success "Dados importados com sucesso no Supabase!"
} else {
    Write-ErrorMsg "Falha ao importar o arquivo SQL no Supabase. Verifique a saída acima."
    exit 1
}
