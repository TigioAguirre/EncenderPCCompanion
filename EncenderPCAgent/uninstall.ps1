<#
Desinstala el servicio EncenderPCAgent.

Ahora esto es solo un atajo: hace lo mismo que correr
"EncenderPCAgent.exe uninstall" a mano (doble click no sirve para
desinstalar porque el .exe interpreta el doble click como "instalar/
reparar"; hay que pasarle el argumento "uninstall" desde una consola,
o usar este script).

No borra las credenciales de emparejamiento (C:\ProgramData\EncenderPCAgent)
por si se vuelve a instalar despues; pasa -BorrarCredenciales para sacarlas
tambien.
#>

param(
    [switch]$BorrarCredenciales
)

$ErrorActionPreference = "Stop"

$exe = Join-Path $PSScriptRoot "EncenderPCAgent.exe"
if (-not (Test-Path $exe)) {
    throw "No se encontro EncenderPCAgent.exe en esta carpeta."
}

& $exe uninstall

if ($BorrarCredenciales) {
    $dataDir = "C:\ProgramData\EncenderPCAgent"
    if (Test-Path $dataDir) {
        Write-Host "Borrando credenciales guardadas en $dataDir..."
        Remove-Item -Recurse -Force $dataDir
    }
}

Write-Host "Listo."
