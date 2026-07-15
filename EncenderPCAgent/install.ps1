<#
Instala EncenderPCAgent como servicio de Windows.
Correr como Administrador, parado en la carpeta del proyecto.
#>

$ErrorActionPreference = "Stop"

$installDir = "C:\Program Files\EncenderPCAgent"
$exeName = "EncenderPCAgent.exe"

Write-Host "1) Publicando el agente (self-contained, no necesita .NET instalado en la PC destino)..."
dotnet publish -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true `
    -o "$installDir"

if (-not (Test-Path "$installDir\$exeName")) {
    throw "No se genero $installDir\$exeName - revisa los errores de 'dotnet publish' arriba."
}

Write-Host "2) Registrando el servicio de Windows..."
$existing = Get-Service -Name "EncenderPCAgent" -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "   Ya existia un servicio EncenderPCAgent, lo detengo y borro antes de recrearlo."
    Stop-Service -Name "EncenderPCAgent" -Force -ErrorAction SilentlyContinue
    sc.exe delete "EncenderPCAgent" | Out-Null
    Start-Sleep -Seconds 2
}

New-Service -Name "EncenderPCAgent" `
    -BinaryPathName "`"$installDir\$exeName`"" `
    -DisplayName "EncenderPC Companion Agent" `
    -Description "Reporta a EncenderPCCompanion que esta PC esta encendida." `
    -StartupType Automatic

Write-Host ""
Write-Host "Instalado en $installDir."
Write-Host ""
Write-Host "Falta emparejar esta PC con tu cuenta antes de arrancar el servicio:"
Write-Host "  cd `"$installDir`""
Write-Host "  .\$exeName pair"
Write-Host ""
Write-Host "Despues de emparejar:"
Write-Host "  Start-Service EncenderPCAgent"
