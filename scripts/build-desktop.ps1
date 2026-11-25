$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
& mvn '-Dmaven.repo.local=D:/keshe/.m2' -B -ntp package dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/desktop-input'
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
Copy-Item target/fat-lab.jar.original target/desktop-input/fat-lab.jar -Force
$destination = Join-Path (Get-Location) ('release/desktop-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
& jpackage --type app-image --name FatLab --input target/desktop-input --main-jar fat-lab.jar --main-class cn.edu.oslab.desktop.DesktopLauncher --dest $destination --app-version 1.2.0 --vendor 'OS Lab' --java-options '-Dfile.encoding=UTF-8' --add-modules ALL-MODULE-PATH
if ($LASTEXITCODE -ne 0) { throw 'Desktop packaging failed' }
if (Get-Command candle.exe -ErrorAction SilentlyContinue) {
    & jpackage --type exe --name FatLab --app-image "$destination/FatLab" --dest $destination --app-version 1.2.0 --vendor 'OS Lab' --win-per-user-install --win-shortcut --win-menu --win-dir-chooser
    if ($LASTEXITCODE -ne 0) { throw 'Installer packaging failed' }
}
Write-Host "Desktop package: $destination"

