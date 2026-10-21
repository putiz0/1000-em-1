# tools/make-pack.ps1 — gera um pack de jogo (zip + manifesto + catálogo remoto)
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\make-pack.ps1 -GameId damas -ContentDir packs-src\damas
#   (opcional) -ContentVersion 2 -BaseUrl "http://localhost:9080"
#
# O que faz:
#   1. monta manifest.json (SHA-256 de cada arquivo do conteúdo);
#   2. zipa conteúdo + manifest para dist\packs-site\packs\{id}-{v}.zip;
#   3. adiciona/atualiza a entrada no remote-catalog.json do servidor.
#
# O pack é DADOS (regras, previsões, cartas, tabuleiros) — nunca código executável.
param(
    [Parameter(Mandatory = $true)][string]$GameId,
    [Parameter(Mandatory = $true)][string]$ContentDir,
    [int]$ContentVersion = 1,
    [string]$BaseUrl = "http://localhost:9080",
    [string]$OutRoot = "$PSScriptRoot\..\dist\packs-site"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$contentDir = [System.IO.Path]::GetFullPath($ContentDir)
if (-not (Test-Path $contentDir -PathType Container)) {
    throw "ContentDir não existe: $contentDir"
}

$outRoot = [System.IO.Path]::GetFullPath($OutRoot)
$packsDir = Join-Path $outRoot "packs"
New-Item -ItemType Directory -Force -Path $packsDir | Out-Null

function Get-Sha256Hex([byte[]]$bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally { $sha.Dispose() }
}

# monta a lista de arquivos (relativa ao contentDir, separador "/")
$entries = @()
Get-ChildItem -LiteralPath $contentDir -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($contentDir.Length).TrimStart('\','/')
    $relative = $relative.Replace('\','/')
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $entries += [PSCustomObject]@{
        path   = $relative
        bytes  = $bytes.Length
        sha256 = Get-Sha256Hex $bytes
    }
}

if ($entries.Count -eq 0) { throw "ContentDir vazio: $contentDir" }

$manifest = [PSCustomObject]@{
    gameId         = $GameId
    contentVersion = $ContentVersion
    files          = $entries
}

# staging: conteúdo + manifest.json na raiz
$staging = Join-Path $outRoot "_staging-$GameId"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null

$manifestJson = $manifest | ConvertTo-Json -Depth 6
# UTF-8 SEM BOM (o parser JSON do app rejeita a marcação EF BB BF)
[System.IO.File]::WriteAllText((Join-Path $staging "manifest.json"), $manifestJson, (New-Object System.Text.UTF8Encoding($false)))
Copy-Item -Path (Join-Path $contentDir '*') -Destination $staging -Recurse -Force

$zipName = "$GameId-$ContentVersion.zip"
$zipPath = Join-Path $packsDir $zipName
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($staging, $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
Remove-Item $staging -Recurse -Force

$zipSize = (Get-Item $zipPath).Length
$zipSha = (Get-FileHash $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()

# atualiza (ou cria) remote-catalog.json
$catalogPath = Join-Path $outRoot "remote-catalog.json"
$catalog = if (Test-Path $catalogPath) {
    Get-Content $catalogPath -Raw | ConvertFrom-Json
} else {
    [PSCustomObject]@{ schemaVersion = 1; baseUrl = $BaseUrl; packs = @() }
}

$packEntry = [PSCustomObject]@{
    gameId         = $GameId
    contentVersion = $ContentVersion
    fileName       = "packs/$zipName"
    packBytes      = $zipSize
    packSha256     = $zipSha
}

$existing = @($catalog.packs | Where-Object { $_.gameId -eq $GameId })
if ($existing.Count -gt 0) {
    $catalog.packs = @($catalog.packs | Where-Object { $_.gameId -ne $GameId }) + @($packEntry)
} else {
    $catalog.packs = @($catalog.packs) + @($packEntry)
}

# UTF-8 SEM BOM (o parser JSON do app rejeita a marcação EF BB BF)
[System.IO.File]::WriteAllText($catalogPath, ($catalog | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Pack gerado com sucesso:"
Write-Host "  zip:      $zipPath"
Write-Host "  tamanho:  $zipSize bytes"
Write-Host "  sha256:   $zipSha"
Write-Host "  catálogo: $catalogPath"
Write-Host ""
Write-Host "Próximos passos:"
Write-Host "  1. rode tools\dev-server.ps1"
Write-Host "  2. adb reverse tcp:9080 tcp:9080 (celular via USB)"
Write-Host "  3. no app, marque o catálogo do jogo $GameId como available (flip catalog.json) e testes"
