param(
    # Pasta de saida dos arquivos wav (res/raw do app)
    [string]$OutDir = "$PSScriptRoot\..\app\src\main\res\raw"
)

$ErrorActionPreference = "Stop"

# PS 5.1: $PSScriptRoot vazio nos defaults de param(); resolve aqui
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot "..\app\src\main\res\raw" }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ==================================================================
# Base: 11025 Hz, 8-bit mono (chiptune, ~11 KB/s - cabe no APK)
# ==================================================================
$RATE = 11025

function Write-Wav([string]$path, [double[]]$samples) {
    $n = $samples.Length
    $data = New-Object byte[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $v = [int][math]::Floor($samples[$i] * 127.0 + 128.5)
        if ($v -lt 0) { $v = 0 }
        if ($v -gt 255) { $v = 255 }
        $data[$i] = [byte]$v
    }
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([char[]]@('R','I','F','F'))
    $bw.Write([int](36 + $n))
    $bw.Write([char[]]@('W','A','V','E'))
    $bw.Write([char[]]@('f','m','t',' '))
    $bw.Write([int]16)
    $bw.Write([int16]1)   # PCM
    $bw.Write([int16]1)   # mono
    $bw.Write([int]$RATE)
    $bw.Write([int]$RATE) # byte rate
    $bw.Write([int16]1)   # block align
    $bw.Write([int16]8)   # bits
    $bw.Write([char[]]@('d','a','t','a'))
    $bw.Write([int]$n)
    $bw.Write($data)
    $bw.Flush()
    [System.IO.File]::WriteAllBytes($path, $ms.ToArray())
    $bw.Dispose(); $ms.Dispose()
}

function New-Buffer([double]$seconds) { return (New-Object double[] ([int]($seconds * $RATE))) }

# freq por nota MIDI (A4 = 69 = 440 Hz)
function Midi-Freq([int]$midi) { return 440.0 * [math]::Pow(2.0, ($midi - 69) / 12.0) }

function Add-Tone([double[]]$buf, [double]$startSec, [double]$durSec, [double]$freq,
                  [double]$amp, [string]$shape) {
    $n0 = [int]($startSec * $RATE)
    $nn = [int]($durSec * $RATE)
    if ($nn -gt ($buf.Length - $n0)) { $nn = $buf.Length - $n0 }
    $ph = 0.0
    $phInc = 2.0 * [math]::PI * $freq / $RATE
    for ($i = 0; $i -lt $nn; $i++) {
        $t = $i / $RATE
        $ph += $phInc
        $v = 0.0
        switch ($shape) {
            'sine'  { $v = [math]::Sin($ph) }
            'tri'   { $x = ($ph / (2*[math]::PI)) % 1.0; if ($x -lt 0) { $x += 1.0 }
                      $v = if ($x -lt 0.5) { 4.0*$x - 1.0 } else { 3.0 - 4.0*$x } }
            'sq'    { $v = if ((($ph / (2*[math]::PI)) % 1.0) -lt 0.5) { 0.45 } else { -0.45 } }
            'pluck' {
                $e = [math]::Exp(-7.0 * $t)
                $h = [math]::Sin($ph) + 0.35 * [math]::Sin(2.0 * $ph) + 0.15 * [math]::Sin(3.0 * $ph)
                $v = $h * $e
            }
            'pad'   {
                # corpo suave com vibrato leve
                $w = [math]::Sin($ph + 0.6*[math]::Sin(0.3*[math]::PI*$freq*$t))
                $e = [math]::Min(1.0, $t / 0.08) * [math]::Exp(-1.2 * $t)
                $v = $w * $e
            }
        }
        # ataque curto para evitar pop
        $at = if ($t -lt 0.004) { $t / 0.004 } else { 1.0 }
        $buf[$n0 + $i] += $v * $amp * $at
    }
}

function Add-NoiseBurst([double[]]$buf, [double]$startSec, [double]$durSec,
                        [double]$amp, [double]$decay, [System.Random]$rng) {
    $n0 = [int]($startSec * $RATE)
    $nn = [int]($durSec * $RATE)
    if ($nn -gt ($buf.Length - $n0)) { $nn = $buf.Length - $n0 }
    for ($i = 0; $i -lt $nn; $i++) {
        $t = $i / $RATE
        $buf[$n0 + $i] += ($rng.NextDouble() * 2.0 - 1.0) * $amp * [math]::Exp(-$decay * $t)
    }
}

function Limit([double[]]$buf, [double]$peak) {
    $max = 0.0
    foreach ($v in $buf) { $a = [math]::Abs($v); if ($a -gt $max) { $max = $a } }
    if ($max -gt 0) {
        $g = $peak / $max
        if ($g -lt 1.0) { for ($i = 0; $i -lt $buf.Length; $i++) { $buf[$i] *= $g } }
    }
}

# ==================================================================
# Musicas (loops originais, grade de compassos 4/4)
# ==================================================================

function Build-MusDamas {
    # contemplativo, la menor, BPM 72
    $bpm = 72.0; $beat = 60.0 / $bpm
    $bars = 4; $beats = $bars * 4
    $buf = New-Buffer ($beats * $beat)
    $bassSeq = 45,45,41,43
    for ($bar = 0; $bar -lt $bars; $bar++) {
        $t0 = $bar * 4 * $beat
        Add-Tone $buf $t0 (2.4*$beat) (Midi-Freq $bassSeq[$bar]) 0.30 'pad'
        Add-Tone $buf ($t0 + 2*$beat) (1.8*$beat) (Midi-Freq ($bassSeq[$bar]+7)) 0.18 'pad'
    }
    $mel = @(
        @(0.0,1.2,69,0.22), @(1.5,1.0,72,0.20), @(3.0,1.0,76,0.22),
        @(4.0,1.5,74,0.16), @(6.0,1.2,69,0.20),
        @(8.0,1.2,65,0.20), @(9.5,1.0,69,0.18), @(11.0,1.2,72,0.20),
        @(12.0,1.5,76,0.22), @(14.0,1.6,72,0.16)
    )
    foreach ($m in $mel) { Add-Tone $buf ($m[0]*$beat) ($m[1]*$beat/2) (Midi-Freq ([int]$m[2])) ([double]$m[3]) 'tri' }
    Limit $buf 0.72
    Write-Wav (Join-Path $OutDir "music_damas.wav") $buf
}

function Build-MusXadrez {
    # tenso, re menor, BPM 60
    $bpm = 60.0; $beat = 60.0 / $bpm
    $bars = 4; $beats = $bars * 4
    $buf = New-Buffer ($beats * $beat)
    for ($bar = 0; $bar -lt $bars; $bar++) {
        $t0 = $bar * 4 * $beat
        Add-Tone $buf $t0 (3.9*$beat) (Midi-Freq 38) 0.30 'pad'
        Add-Tone $buf $t0 (3.9*$beat) (Midi-Freq 45) 0.14 'pad'
    }
    $mel = @(
        @(0.0,2.0,62,0.20), @(3.0,1.2,65,0.18), @(5.0,1.8,69,0.20),
        @(8.0,1.2,67,0.16), @(9.5,1.2,65,0.14), @(11.0,1.8,62,0.18),
        @(14.0,1.8,60,0.16)
    )
    foreach ($m in $mel) { Add-Tone $buf ($m[0]*$beat) ($m[1]*$beat/2) (Midi-Freq ([int]$m[2])) ([double]$m[3]) 'tri' }
    Limit $buf 0.70
    Write-Wav (Join-Path $OutDir "music_xadrez.wav") $buf
}

function Build-MusDomino {
    # leve saracoteando, sol maior pentatonico, BPM 96
    $bpm = 96.0; $beat = 60.0 / $bpm
    $bars = 4; $beats = $bars * 4
    $buf = New-Buffer ($beats * $beat)
    $rng = New-Object System.Random 71045
    for ($bar = 0; $bar -lt $bars; $bar++) {
        $t0 = $bar * 4 * $beat
        Add-Tone $buf $t0 (0.9*$beat) (Midi-Freq 31) 0.34 'pluck'
        Add-Tone $buf ($t0+1.5*$beat) (0.6*$beat) (Midi-Freq 38) 0.22 'pluck'
        Add-Tone $buf ($t0+2.5*$beat) (0.6*$beat) (Midi-Freq 31) 0.30 'pluck'
        Add-Tone $buf ($t0+3.5*$beat) (0.5*$beat) (Midi-Freq 38) 0.20 'pluck'
        Add-NoiseBurst $buf ($t0 + 0.5*$beat) 0.05 0.10 40 $rng
        Add-NoiseBurst $buf ($t0 + 2.5*$beat) 0.05 0.08 40 $rng
    }
    $mel = @(
        @(0.5,0.5,67,0.30), @(1.0,0.5,71,0.26), @(1.5,0.5,74,0.30),
        @(2.5,0.5,76,0.26), @(3.0,0.5,74,0.22),
        @(4.5,0.5,69,0.26), @(5.0,0.5,71,0.26), @(5.5,0.5,67,0.24),
        @(7.0,1.0,62,0.26),
        @(8.5,0.5,67,0.30), @(9.0,0.5,74,0.28), @(9.5,0.5,76,0.24),
        @(10.5,0.5,74,0.24), @(11.0,0.5,71,0.24),
        @(12.5,0.5,69,0.26), @(13.0,0.5,67,0.26), @(14.0,1.5,62,0.26)
    )
    foreach ($m in $mel) { Add-Tone $buf ($m[0]*$beat) ($m[1]*$beat/3) (Midi-Freq ([int]$m[2])) ([double]$m[3]) 'pluck' }
    Limit $buf 0.76
    Write-Wav (Join-Path $OutDir "music_domino.wav") $buf
}

function Build-MusImperio {
    # animada de dinheiro, do maior, BPM 112
    $bpm = 112.0; $beat = 60.0 / $bpm
    $bars = 8; $beats = $bars * 4
    $buf = New-Buffer ($beats * $beat)
    $rng = New-Object System.Random 52026
    for ($s = 0; $s -lt $beats*2; $s += 2) {
        $midi = 36; if (($s / 2) % 2 -eq 1) { $midi = 43 }
        Add-Tone $buf ($s*0.5*$beat) (0.45*$beat) (Midi-Freq $midi) 0.34 'sq'
        Add-NoiseBurst $buf ($s*0.5*$beat + 0.95*$beat) 0.03 0.07 60 $rng
    }
    $mel = @(
        @(0.0,0.8,64,0.20), @(1.0,0.8,65,0.18), @(2.0,1.6,67,0.22), @(4.0,0.8,72,0.24),
        @(5.0,0.8,71,0.18), @(6.0,1.6,67,0.20),
        @(8.0,0.8,64,0.20), @(9.0,0.8,62,0.18), @(10.0,1.6,65,0.22), @(12.0,0.8,67,0.22),
        @(13.0,0.8,65,0.18), @(14.0,1.6,64,0.20),
        @(16.0,0.8,60,0.22), @(17.0,0.8,64,0.18), @(18.0,1.6,67,0.24), @(20.0,0.8,72,0.24),
        @(21.0,0.8,76,0.22), @(22.0,1.6,74,0.20),
        @(24.0,0.8,72,0.22), @(25.0,0.8,74,0.18), @(26.0,1.6,72,0.20), @(28.0,0.8,67,0.20),
        @(29.0,0.8,65,0.18), @(30.0,1.6,64,0.20)
    )
    foreach ($m in $mel) { Add-Tone $buf ($m[0]*$beat) ($m[1]*$beat/2) (Midi-Freq ([int]$m[2])) ([double]$m[3]) 'tri' }
    Limit $buf 0.74
    Write-Wav (Join-Path $OutDir "music_imperio.wav") $buf
}

function Build-MusSushi {
    # koto pentatonico, la menor, BPM 132
    $bpm = 132.0; $beat = 60.0 / $bpm
    $bars = 8; $beats = $bars * 4
    $buf = New-Buffer ($beats * $beat)
    $rng = New-Object System.Random 34019
    for ($s = 0; $s -lt $beats*2; $s++) {
        if ($s % 2 -eq 0) {
            Add-Tone $buf ($s*0.5*$beat) (0.3*$beat) (Midi-Freq 45) 0.26 'pluck'
        }
        Add-NoiseBurst $buf ($s*0.5*$beat) 0.03 0.05 55 $rng
    }
    $mel = @(
        @(0.0,0.5,69,0.30), @(0.5,0.5,72,0.26), @(1.0,1.0,74,0.30),
        @(2.0,0.5,76,0.24), @(2.5,0.5,72,0.26),
        @(3.0,1.0,69,0.28), @(5.0,0.5,79,0.26), @(5.5,0.5,76,0.24),
        @(6.0,1.0,74,0.30), @(8.0,0.5,72,0.26), @(8.5,0.5,69,0.26),
        @(9.0,1.0,67,0.26), @(10.0,0.5,69,0.26), @(10.5,0.5,72,0.24),
        @(11.0,1.0,74,0.28),
        @(12.5,0.5,76,0.24), @(13.0,0.5,74,0.26), @(13.5,0.5,72,0.26), @(14.0,1.0,69,0.28),
        @(16.0,0.5,79,0.28), @(16.5,0.5,76,0.26), @(17.0,1.0,74,0.30),
        @(18.0,0.5,72,0.26), @(18.5,0.5,74,0.24), @(19.0,1.0,76,0.30),
        @(20.0,0.5,79,0.26), @(20.5,0.5,79,0.22),
        @(21.0,1.5,74,0.28), @(23.0,0.5,72,0.24), @(23.5,0.5,69,0.26), @(24.0,1.5,67,0.26),
        @(26.0,0.5,69,0.26), @(26.5,0.5,72,0.26), @(27.0,1.0,74,0.30), @(28.0,0.5,76,0.24),
        @(28.5,0.5,79,0.24), @(29.0,1.5,81,0.30), @(31.0,1.0,79,0.24)
    )
    foreach ($m in $mel) { Add-Tone $buf ($m[0]*$beat) ($m[1]*$beat/3) (Midi-Freq ([int]$m[2])) ([double]$m[3]) 'pluck' }
    Limit $buf 0.78
    Write-Wav (Join-Path $OutDir "music_sushi.wav") $buf
}

# ==================================================================
# Efeitos sonoros
# ==================================================================

function Build-SfxTap {
    $buf = New-Buffer 0.09
    Add-Tone $buf 0.0 0.05 950 0.5 'sine'
    Add-Tone $buf 0.01 0.04 700 0.25 'sine'
    Limit $buf 0.7
    Write-Wav (Join-Path $OutDir "sfx_tap.wav") $buf
}

function Build-SfxMove {
    $buf = New-Buffer 0.15
    Add-Tone $buf 0.0 0.08 540 0.4 'sine'
    Add-Tone $buf 0.015 0.07 380 0.22 'sine'
    Add-NoiseBurst $buf 0.0 0.02 0.30 90 (New-Object System.Random 11)
    Limit $buf 0.7
    Write-Wav (Join-Path $OutDir "sfx_move.wav") $buf
}

function Build-SfxCapture {
    $buf = New-Buffer 0.32
    Add-NoiseBurst $buf 0.0 0.09 0.65 26 (New-Object System.Random 22)
    Add-Tone $buf 0.005 0.22 105 0.5 'sine'
    Add-Tone $buf 0.02 0.18 160 0.3 'sine'
    Limit $buf 0.8
    Write-Wav (Join-Path $OutDir "sfx_capture.wav") $buf
}

function Build-SfxStone {
    $buf = New-Buffer 0.18
    Add-Tone $buf 0.0 0.03 1250 0.5 'sine'
    Add-Tone $buf 0.012 0.09 660 0.28 'sine'
    Add-NoiseBurst $buf 0.0 0.06 0.35 55 (New-Object System.Random 33)
    Limit $buf 0.72
    Write-Wav (Join-Path $OutDir "sfx_stone.wav") $buf
}

function Build-SfxDice {
    $buf = New-Buffer 0.5
    $rng = New-Object System.Random 44
    Add-NoiseBurst $buf 0.00 0.11 0.62 22 $rng
    Add-NoiseBurst $buf 0.14 0.08 0.50 26 $rng
    Add-NoiseBurst $buf 0.25 0.13 0.58 18 $rng
    Add-Tone $buf 0.26 0.20 240 0.28 'sine'
    Limit $buf 0.8
    Write-Wav (Join-Path $OutDir "sfx_dice.wav") $buf
}

function Build-SfxCard {
    $buf = New-Buffer 0.26
    $rng = New-Object System.Random 55
    Add-NoiseBurst $buf 0.02 0.13 0.55 16 $rng
    Add-Tone $buf 0.02 0.12 2600 0.10 'sine'
    Add-Tone $buf 0.10 0.09 1900 0.08 'sine'
    Limit $buf 0.72
    Write-Wav (Join-Path $OutDir "sfx_card.wav") $buf
}

function Build-SfxWin {
    $buf = New-Buffer 1.3
    $seq = @(
        @(0.0, 60), @(0.18, 64), @(0.36, 67), @(0.54, 72),
        @(0.85, 67), @(1.05, 72), @(1.20, 76)
    )
    foreach ($s in $seq) { Add-Tone $buf $s[0] 0.22 (Midi-Freq $s[1]) 0.30 'tri' }
    Add-Tone $buf 0.54 0.7 (Midi-Freq 48) 0.22 'pad'
    Add-Tone $buf 0.85 0.55 (Midi-Freq 55) 0.20 'pad'
    Limit $buf 0.85
    Write-Wav (Join-Path $OutDir "sfx_win.wav") $buf
}

function Build-SfxLose {
    $buf = New-Buffer 1.2
    $seq = @(
        @(0.0, 69), @(0.25, 67), @(0.50, 65), @(0.75, 62), @(1.0, 57)
    )
    foreach ($s in $seq) { Add-Tone $buf $s[0] 0.30 (Midi-Freq $s[1]) 0.26 'tri' }
    Limit $buf 0.70
    Write-Wav (Join-Path $OutDir "sfx_lose.wav") $buf
}

function Build-SfxCoin {
    $buf = New-Buffer 0.34
    Add-Tone $buf 0.00 0.09 1320 0.40 'sine'
    Add-Tone $buf 0.06 0.20 1760 0.35 'sine'
    Add-NoiseBurst $buf 0.0 0.02 0.20 80 (New-Object System.Random 66)
    Limit $buf 0.75
    Write-Wav (Join-Path $OutDir "sfx_coin.wav") $buf
}

function Build-SfxDoor {
    $buf = New-Buffer 0.42
    Add-Tone $buf 0.00 0.13 145 0.55 'sine'
    Add-Tone $buf 0.00 0.13 110 0.35 'sine'
    Add-NoiseBurst $buf 0.0 0.05 0.35 40 (New-Object System.Random 77)
    Add-Tone $buf 0.22 0.15 145 0.55 'sine'
    Add-Tone $buf 0.22 0.15 110 0.35 'sine'
    Add-NoiseBurst $buf 0.22 0.05 0.35 40 (New-Object System.Random 78)
    Limit $buf 0.8
    Write-Wav (Join-Path $OutDir "sfx_door.wav") $buf
}

# ==================================================================
# Executa tudo
# ==================================================================

Build-MusDamas
Build-MusXadrez
Build-MusDomino
Build-MusImperio
Build-MusSushi

Build-SfxTap
Build-SfxMove
Build-SfxCapture
Build-SfxStone
Build-SfxDice
Build-SfxCard
Build-SfxWin
Build-SfxLose
Build-SfxCoin
Build-SfxDoor

Write-Output "===Arquivos gerados==="
Get-ChildItem $OutDir -Filter *.wav | ForEach-Object {
    "{0,7} bytes  {1}" -f $_.Length, $_.Name
}
