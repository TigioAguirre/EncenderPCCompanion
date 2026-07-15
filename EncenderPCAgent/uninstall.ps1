<#
Desinstala el servicio EncenderPCAgent. Correr como Administrador.
No borra las credenciales de emparejamiento (C:\ProgramData\EncenderPCAgent)
por si volves a instalar despues; pasa -BorrarCredenciales para sacarlas tambien.
#>

param(
    [switch]$BorrarCredenciales
)

$ErrorActionPreference = "Stop"

$existing = Get-Service -Name "EncenderPCAgent" -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Deteniendo y borrando el servicio..."
    Stop-Service -Name "EncenderPCAgent" -Force -ErrorAction SilentlyContinue
    sc.exe delete "EncenderPCAgent" | Out-Null
} else {
    Write-Host "El servicio EncenderPCAgent no estaba instalado."
}

$installDir = "C:\Program Files\EncenderPCAgent"
if (Test-Path $installDir) {
    Write-Host "Borrando $installDir..."
    Remove-Item -Recurse -Force $installDir
}

if ($BorrarCredenciales) {
    $dataDir = "C:\ProgramData\EncenderPCAgent"
    if (Test-Path $dataDir) {
        Write-Host "Borrando credenciales guardadas en $dataDir..."
        Remove-Item -Recurse -Force $dataDir
    }
}

Write-Host "Listo."
