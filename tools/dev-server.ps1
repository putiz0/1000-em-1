# tools/dev-server.ps1 — servidor de packs para desenvolvimento (Fase 3)
#
# Serve a pasta dist\packs-site\ por HTTP na porta 9080:
#   /remote-catalog.json         -> catálogo dos packs disponíveis
#   /packs/{id}-{versao}.zip     -> conteúdo do jogo
#
# Uso (PowerShell):
#   powershell -ExecutionPolicy Bypass -File tools\dev-server.ps1
#
# No aparelho conectado por USB:
#   adb reverse tcp:9080 tcp:9080      (o app fala com "localhost:9080" e fecha no PC)
#
# Suporta: GET, HEAD e Range (bytes=N-) para testar PAUSAR/RETOMAR de verdade.
param(
    [int]$Port = 9080,
    [string]$SiteRoot = "$PSScriptRoot\..\dist\packs-site"
)

$ErrorActionPreference = "Stop"
$siteRoot = [System.IO.Path]::GetFullPath($SiteRoot)

if (-not (Test-Path $siteRoot)) {
    Write-Host "Criando raiz do site: $siteRoot"
    New-Item -ItemType Directory -Force -Path $siteRoot | Out-Null
}

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "PlayBox dev server rodando em http://localhost:$Port/  (Ctrl+C para sair)"
Write-Host "Raiz: $siteRoot"

$mimeTypes = @{
    ".json" = "application/json"; ".zip" = "application/zip"; ".txt" = "text/plain"
    ".png"  = "image/png"; ".jpg" = "image/jpeg"; ".webp" = "image/webp"
    ".svg"  = "image/svg+xml"; ".md" = "text/markdown"
}

while ($listener.IsListening) {
    $context = $listener.GetContext()
    $request = $context.Request
    $response = $context.Response

    try {
        # caminho seguro (impede subida de diretório "..\")
        $relative = $request.Url.AbsolutePath.TrimStart('/')
        $relative = $relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        if ($relative -match "\.\.") { $response.StatusCode = 403; $response.OutputStream.Close(); continue }

        $fullPath = [System.IO.Path]::GetFullPath((Join-Path $siteRoot $relative))
        if (-not ($fullPath.StartsWith($siteRoot)) -or -not (Test-Path $fullPath -PathType Leaf)) {
            $response.StatusCode = 404
            $response.StatusDescription = "Not found"
            $response.OutputStream.Close()
            continue
        }

        $fileLength = (Get-Item $fullPath).Length
        $extension = [System.IO.Path]::GetExtension($fullPath).ToLowerInvariant()
        $response.ContentType = if ($mimeTypes.ContainsKey($extension)) { $mimeTypes[$extension] } else { "application/octet-stream" }
        $response.Headers["Accept-Ranges"] = "bytes"

        $range = $request.Headers["Range"]
        if ($request.HttpMethod -eq "HEAD") {
            $response.ContentLength64 = $fileLength
            $response.StatusCode = 200
            $response.OutputStream.Close()
            continue
        }

        if ($range -and ($range -match "^bytes=(\d+)-")) {
            $start = [int64]$Matches[1]
            if ($start -ge $fileLength) {
                $response.StatusCode = 416
                $response.OutputStream.Close()
                continue
            }
            $length = $fileLength - $start
            $response.StatusCode = 206
            $response.Headers["Content-Range"] = "bytes $start-$($fileLength - 1)/$fileLength"
            $response.ContentLength64 = $length
            $stream = [System.IO.FileStream]::new($fullPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
            try {
                $stream.Position = $start
                $buffer = [byte[]]::new(64KB)
                $remaining = $length
                while ($remaining -gt 0) {
                    $chunk = $stream.Read($buffer, 0, [Math]::Min($buffer.Length, $remaining))
                    if ($chunk -le 0) { break }
                    $response.OutputStream.Write($buffer, 0, $chunk)
                    $remaining -= $chunk
                }
            } finally {
                $stream.Dispose()
            }
            Write-Host ("{0} 206 {1} bytes de {2}" -f $request.RequestUri, $length, $relative)
        } else {
            $response.StatusCode = 200
            $response.ContentLength64 = $fileLength
            $stream = [System.IO.FileStream]::new($fullPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
            try {
                $buffer = [byte[]]::new(64KB)
                while ($true) {
                    $chunk = $stream.Read($buffer, 0, $buffer.Length)
                    if ($chunk -le 0) { break }
                    $response.OutputStream.Write($buffer, 0, $chunk)
                }
            } finally {
                $stream.Dispose()
            }
            Write-Host ("{0} 200 {1} bytes de {2}" -f $request.RequestUri, $fileLength, $relative)
        }
    } catch {
        try { $response.StatusCode = 500; $response.OutputStream.Close() } catch {}
        Write-Host "ERRO: $_"
    } finally {
        try { $response.Close() } catch {}
    }
}
