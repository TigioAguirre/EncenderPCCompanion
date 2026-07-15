# EncenderPCAgent

Servicio de Windows que reporta a EncenderPCCompanion que esta PC está
encendida, mandando un "heartbeat" a Firestore cada 30 segundos.

## Cómo funciona

1. Corrés `EncenderPCAgent.exe pair` una sola vez → te pide el código de
   6 dígitos que te mostró la app → lo cambia por credenciales (Firebase
   Auth) que **solo** le permiten escribir `status`/`lastSeen` de esta PC
   puntual → las guarda en `C:\ProgramData\EncenderPCAgent\device.json`.
2. El servicio de Windows arranca solo con el sistema y, mientras esté
   vivo, cada 30s hace `PATCH` a Firestore poniendo `status: "online"`.
3. Si la PC se apaga prolijamente, el servicio intenta mandar un último
   `status: "offline"` antes de morir. Si el corte es abrupto (falla de
   luz, crash), no llega a avisar — para eso está la Cloud Function
   `checkOfflineDevices`, que marca offline cualquier PC sin heartbeat
   hace más de 90s.

## Requisitos para compilar

- [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0) (no
  alcanza con el runtime, necesitás el SDK completo para compilar).
- Windows 10/11 con PowerShell.

## Configuración antes de instalar

Abrí `appsettings.json` y completá `ApiKey` con el **Web API Key** de tu
proyecto Firebase:

1. [Firebase Console](https://console.firebase.google.com/) → tu proyecto
   (`encenderpc-companion`) → ⚙️ Configuración del proyecto → pestaña
   "General".
2. Si no tenés ninguna app Web todavía, hacé click en el ícono `</>` para
   agregar una (no hace falta que hagas nada con el hosting, solo
   registrarla) — eso te va a mostrar un bloque `firebaseConfig` con
   `apiKey: "..."`.
3. Pegá ese valor en `appsettings.json`:

```json
"Firebase": {
  "ProjectId": "encenderpc-companion",
  "ApiKey": "AIza....................",
  "FunctionsRegion": "us-central1"
}
```

> Este ApiKey **no es secreto** — identifica el proyecto, no da permisos
> por sí solo. Lo que protege tus datos son las `firestore.rules` que ya
> desplegamos.

## Instalar como servicio

Desde una consola de PowerShell **como Administrador**, parado en la
carpeta del proyecto (`EncenderPCAgent/`, donde está el `.csproj`):

```powershell
.\install.ps1
```

Esto compila el agente (self-contained, no hace falta instalar .NET en
la PC final) y lo registra como servicio de Windows en modo automático.

## Emparejar la PC

El script te va a decir el path exacto, algo así:

```powershell
cd "C:\Program Files\EncenderPCAgent"
.\EncenderPCAgent.exe pair
```

Te pide el código de 6 dígitos → pegalo → listo.

## Arrancar el servicio

```powershell
Start-Service EncenderPCAgent
```

Comprobar que esté corriendo:

```powershell
Get-Service EncenderPCAgent
```

Ver sus logs (van al Visor de eventos de Windows):

```powershell
Get-EventLog -LogName Application -Source "EncenderPCAgent" -Newest 20
```

## Reinstalar / actualizar el código

Volvé a correr `.\install.ps1` — reemplaza el ejecutable y el servicio,
pero **no toca** `C:\ProgramData\EncenderPCAgent\device.json`, así que
no hace falta volver a emparejar.

## Desinstalar

```powershell
.\uninstall.ps1
```

Agregá `-BorrarCredenciales` si además querés borrar el emparejamiento:

```powershell
.\uninstall.ps1 -BorrarCredenciales
```

## Estructura

```
EncenderPCAgent.csproj
appsettings.json          Config pública (projectId, apiKey de Firebase)
Program.cs                 Entry point: modo "pair" vs modo servicio
Worker.cs                   Loop de heartbeat + renovación de token
Config/
  AgentConfig.cs              POCOs de configuración
  ConfigStore.cs               Guarda/lee device.json en ProgramData
Firebase/
  FirebaseAuthClient.cs         signInWithCustomToken + refresh
  PairingClient.cs               Llama a la Cloud Function pairDevice
  FirestoreHeartbeatClient.cs     PATCH de status/lastSeen a Firestore
Pairing/
  PairingFlow.cs                  Flujo interactivo de consola
install.ps1                Publica + registra el servicio
uninstall.ps1               Da de baja el servicio
```
