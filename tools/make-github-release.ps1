param(
    # Endereco publico do Release (ex.: https://github.com/USER/REPO/releases/download/packs-v1)
    [Parameter(Mandatory = $true)][string]$ReleaseUrl,
    # Site de packs local de onde copiamos os zips (tem que ser o MESMO que gerou os hashes)
    [string]$InRoot = "",
    # Pasta de saida com os arquivos planos para arrastar no GitHub Releases
    [string]$OutRoot = ""
)

$ErrorActionPreference = "Stop"

# PS 5.1: $PSScriptRoot nao existe dentro dos defaults de param(); resolve aqui
if (-not $InRoot)  { $InRoot  = Join-Path $PSScriptRoot "..\dist\packs-site" }
if (-not $OutRoot) { $OutRoot = Join-Path $PSScriptRoot "..\dist\packs-github" }

$InRoot = [System.IO.Path]::GetFullPath($InRoot)
$OutRoot = [System.IO.Path]::GetFullPath($OutRoot)
$ReleaseUrl = $ReleaseUrl.TrimEnd('/')

$catalogPath = Join-Path $InRoot "remote-catalog.json"
if (-not (Test-Path $catalogPath)) { throw "Catalogo nao encontrado: $catalogPath" }
$catalog = Get-Content $catalogPath -Raw | ConvertFrom-Json

if ($catalog.schemaVersion -ne 1) { throw "schemaVersion inesperado: $($catalog.schemaVersion)" }

New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null

function Get-Sha256Hex([byte[]]$bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally { $sha.Dispose() }
}

$outputPacks = @()
foreach ($pack in $catalog.packs) {
    $flatName = ($pack.fileName -replace '^packs/', '')
    $destination = Join-Path $OutRoot $flatName
    $source = Join-Path (Join-Path $InRoot "packs") $flatName

    if (-not (Test-Path $source)) { throw "Pack nao encontrado: $source" }

    # Integridade: o zip copiado tem que bater com o hash gravado no catalogo
    $computed = Get-Sha256Hex ([System.IO.File]::ReadAllBytes($source))
    if ($computed -ne $pack.packSha256) {
        throw "SHA-256 divergente em $flatName - regenere com make-pack.ps1 antes de publicar."
    }

    Copy-Item $source $destination -Force

    $outputPacks += [PSCustomObject]@{
        gameId         = $pack.gameId
        contentVersion = $pack.contentVersion
        fileName       = $flatName
        packBytes      = $pack.packBytes
        packSha256     = $pack.packSha256
    }
}

# Catalogo remoto apontando para o Release, nomes planos (Release do GitHub nao tem subpastas)
$remoteCatalog = [PSCustomObject]@{
    schemaVersion = 1
    baseUrl       = $ReleaseUrl
    packs         = $outputPacks
}

$json = $remoteCatalog | ConvertTo-Json -Depth 6
$catalogOut = Join-Path $OutRoot "remote-catalog.json"
# UTF-8 SEM BOM (o parser do aplicativo nao aceita BOM)
[System.IO.File]::WriteAllText($catalogOut, $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Output "Pasta pronta: $OutRoot"
Get-ChildItem $OutRoot | ForEach-Object {
    "{0,10} bytes  {1}" -f $_.Length, $_.Name
}
Write-Output "baseUrl no catalogo: $ReleaseUrl"
