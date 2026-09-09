# ╔══════════════════════════════════════════════════════════════╗
# ║         ClassBridge AI — Backend Startup Script              ║
# ║  Reads .env automatically and starts Spring Boot backend     ║
# ╚══════════════════════════════════════════════════════════════╝

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$EnvFile   = Join-Path $ScriptDir ".env"
$BackendDir = Join-Path $ScriptDir "backend"

Write-Host ""
Write-Host "  ╔═══════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║   ClassBridge AI — Backend    ║" -ForegroundColor Cyan
Write-Host "  ╚═══════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── Load .env ────────────────────────────────────────────────────────────────
if (-Not (Test-Path $EnvFile)) {
    Write-Host "  ✗ .env not found. Copy .env.example to .env and fill in your values." -ForegroundColor Red
    Read-Host "  Press Enter to exit"; exit 1
}
Write-Host "  ► Loading .env ..." -ForegroundColor DarkGray
Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Count -eq 2) { Set-Item -Path "Env:\$($parts[0].Trim())" -Value $parts[1].Trim() }
}
Write-Host "  ✓ Environment loaded" -ForegroundColor Green

# ── Validate required vars ───────────────────────────────────────────────────
$missing = @()
if (-not $env:GEMINI_API_KEY) { $missing += "GEMINI_API_KEY" }
if ($missing.Count -gt 0) {
    Write-Host "  ⚠ Missing env vars: $($missing -join ', ')" -ForegroundColor Yellow
}

# ── Check MongoDB ─────────────────────────────────────────────────────────────
Write-Host "  ► Checking MongoDB (port 27017) ..." -ForegroundColor DarkGray
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("localhost", 27017); $tcp.Close()
    Write-Host "  ✓ MongoDB running" -ForegroundColor Green
} catch {
    Write-Host "  ✗ MongoDB is NOT running! Start MongoDB first." -ForegroundColor Red
    Read-Host "  Press Enter to exit"; exit 1
}

# ── Check Java / Maven ───────────────────────────────────────────────────────
try { java -version 2>$null; Write-Host "  ✓ Java found" -ForegroundColor Green } catch { Write-Host "  ✗ Java not found" -ForegroundColor Red; exit 1 }
try { mvn -version 2>$null; Write-Host "  ✓ Maven found" -ForegroundColor Green } catch { Write-Host "  ✗ Maven not found" -ForegroundColor Red; exit 1 }

# ── Build JVM -D args from .env (ensures all vars reach Spring Boot) ──────────
$jvmArgs = @()
Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Count -eq 2) {
        $key   = $parts[0].Trim()
        $value = $parts[1].Trim()
        $jvmArgs += "-D$key=$value"
    }
}
$jvmArgsStr = $jvmArgs -join " "

# ── Start ────────────────────────────────────────────────────────────────────
$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }
Write-Host ""
Write-Host "  ► Starting backend on port $port ..." -ForegroundColor Cyan
Write-Host "  ► Health: http://localhost:$port/api/health" -ForegroundColor Gray
Write-Host "  Press Ctrl+C to stop" -ForegroundColor DarkGray
Write-Host ""
Set-Location $BackendDir
& mvn spring-boot:run "-Dspring-boot.run.jvmArguments=$jvmArgsStr"
