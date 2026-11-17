$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $projectRoot 'data/server.pid'
if (!(Test-Path -LiteralPath $pidFile)) { throw 'No managed server PID. Stop the manually started process in its console.' }
$serverPid = [int](Get-Content -LiteralPath $pidFile)
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid"
$expectedJar = Join-Path $projectRoot 'release/fat-lab.jar'
if (!$process -or $process.Name -notin @('java.exe','javaw.exe') -or !$process.CommandLine.Contains($expectedJar)) { throw 'PID does not match this project; nothing was stopped.' }
$state = Invoke-RestMethod 'http://127.0.0.1:8080/api/state'
if ($state.ready) { Invoke-RestMethod 'http://127.0.0.1:8080/api/operations' -Method Post -ContentType 'application/json' -Body '{"operation":"close_all"}' | Out-Null }
Stop-Process -Id $serverPid
Remove-Item -LiteralPath $pidFile
Write-Host 'All files saved. FAT Lab stopped.'
