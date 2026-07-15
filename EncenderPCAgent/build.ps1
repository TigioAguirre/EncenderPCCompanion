<#
Script para DESARROLLADORES: genera el EncenderPCAgent.exe final que se le
va a repartir a los usuarios (self-contained, un solo archivo, no necesita
.NET instalado en la PC destino).

El usuario final NO corre este script: solo recibe el .exe que este script
deja en .\publish\EncenderPCAgent.exe y hace doble click en él (o lo copia
a "C:\Program Files\EncenderPCAgent" primero, si querés que quede prolijo).
#>

$ErrorActionPreference = "Stop"

$outDir = ".\publish"

Write-Host "Publicando EncenderPCAgent.exe (self-contained, un solo archivo)..."
dotnet publish -c Release -o "$outDir"

if (-not (Test-Path "$outDir\EncenderPCAgent.exe")) {
    throw "No se genero $outDir\EncenderPCAgent.exe - revisa los errores de 'dotnet publish' arriba."
}

Write-Host ""
Write-Host "Listo: $outDir\EncenderPCAgent.exe"
Write-Host ""
Write-Host "Para instalarlo en una PC: copiar ese .exe (junto con appsettings.json,"
Write-Host "que ya queda al lado) a la PC destino y hacer doble click en él."
Write-Host "El propio programa pide permisos de Administrador, se instala como"
Write-Host "servicio de Windows y guia el emparejamiento con la app."
