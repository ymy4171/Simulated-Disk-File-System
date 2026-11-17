$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$jar = Join-Path $projectRoot 'release/fat-lab.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Missing release/fat-lab.jar. Run build.cmd first.' }
$java = (Get-Command java -ErrorAction Stop).Source
$version = (& $java --version | Out-String)
if ($version -notmatch '(?:openjdk|java) (\d+)' -or [int]$Matches[1] -lt 21) { throw 'Java 21 or newer is required.' }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'data') | Out-Null
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($listener) { throw 'Port 8080 is already in use. If FAT Lab is running, open http://127.0.0.1:8080 or run stop.cmd.' }
$process = Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot 'data/server.log') -RedirectStandardError (Join-Path $projectRoot 'data/server-error.log') -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $projectRoot 'data/server.pid')
for ($attempt=0; $attempt -lt 40; $attempt++) {
    if ($process.HasExited) { throw 'Startup failed. See data/server.log and data/server-error.log.' }
    try { $response = Invoke-WebRequest 'http://127.0.0.1:8080/api/state' -UseBasicParsing -TimeoutSec 1; if ($response.StatusCode -eq 200) { Start-Process 'http://127.0.0.1:8080'; Write-Host 'FAT Lab ready. Use stop.cmd to save and stop.'; exit 0 } } catch {}
    Start-Sleep -Milliseconds 500
}
throw 'Startup timeout. Check data/server.log.'
