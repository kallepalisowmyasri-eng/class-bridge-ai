# ╔══════════════════════════════════════════════════════════════╗
# ║       ClassBridge AI — Whisper Service Startup Script        ║
# ║  Starts FastAPI/uvicorn Whisper transcription microservice   ║
# ╚══════════════════════════════════════════════════════════════╝

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$EnvFile   = Join-Path $ScriptDir ".env"
$ServiceDir = Join-Path $ScriptDir "transcription-service"

Write-Host ""
Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║   ClassBridge AI — Whisper Service       ║" -ForegroundColor Cyan
Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Load .env if present
if (Test-Path $EnvFile) {
    Write-Host "  ► Loading .env ..." -ForegroundColor DarkGray
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $parts = $line.Split("=", 2)
        if ($parts.Count -eq 2) { Set-Item -Path "Env:\$($parts[0].Trim())" -Value $parts[1].Trim() }
    }
    Write-Host "  ✓ Environment loaded" -ForegroundColor Green
}

# Verify Python
try {
    $pyVer = python --version 2>&1
    Write-Host "  ✓ Python found: $pyVer" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Python not found! Please install Python 3.10+ and add to PATH." -ForegroundColor Red
    exit 1
}

# Verify FFmpeg
try {
    $ffmpegVer = ffmpeg -version 2>&1 | Select-Object -First 1
    Write-Host "  ✓ FFmpeg found: $ffmpegVer" -ForegroundColor Green
} catch {
    Write-Host "  ⚠ FFmpeg not found on PATH. Faster-whisper audio extraction may fail." -ForegroundColor Yellow
}

$port = if ($env:WHISPER_PORT) { $env:WHISPER_PORT } else { "5000" }
$hostAddress = if ($env:WHISPER_HOST) { $env:WHISPER_HOST } else { "0.0.0.0" }

Write-Host ""
Write-Host "  ► Starting Whisper service on port $port..." -ForegroundColor Cyan
Write-Host "  ► Health check: http://localhost:$port/health" -ForegroundColor Gray
Write-Host "  ► Transcribe:   http://localhost:$port/transcribe" -ForegroundColor Gray
Write-Host ""

Set-Location $ServiceDir
python -m uvicorn whisper_service:app --host $hostAddress --port $port
