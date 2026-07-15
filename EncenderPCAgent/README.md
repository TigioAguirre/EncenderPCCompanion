# EncenderPCAgent

Servicio de Windows que reporta a EncenderPCCompanion que esta PC está
encendida, mandando un "heartbeat" a Firestore cada 30 segundos.

## Para el usuario final (no hace falta CLI ni saber de PowerShell)

1. Te pasan un solo archivo: `EncenderPCAgent.exe` (junto con
   `appsettings.json` al lado, en la misma carpeta).
2. Hacés **doble click** en `EncenderPCAgent.exe`.
3. Windows va a pedir permiso de Administrador (UAC) — aceptá. Esto es
   necesario para instalar el servicio, no para nada más.
4. El programa instala el servicio, y si la PC todavía no está
   emparejada te pide el código de 6 dígitos que te muestra la app.
   Pegalo y listo.
5. Cerrás la ventana. El agente queda corriendo solo desde ahí en
   adelante, incluso después de reiniciar la PC.

Si necesitás **volver a emparejar** esa misma PC (por ejemplo, la
sacaste de tu cuenta y la agregaste de nuevo), volvé a hacer doble click
en `EncenderPCAgent.exe`: te va a preguntar si querés re-emparejarla y,
al terminar, reinicia el servicio solo para que dependa de las
credenciales nuevas — no hace falta reiniciar Windows para que deje de
figurar como apagada.

Para **desinstalarlo**, corré desde una consola:

```powershell
EncenderPCAgent.exe uninstall
```

(el doble click normal es para instalar/reparar, así que desinstalar
necesita este argumento explícito, o el script `uninstall.ps1` incluido).

## Cómo funciona por dentro

1. El emparejamiento cambia el código de 6 dígitos por credenciales de
   Firebase Auth que **solo** le permiten escribir `status`/`lastSeen` de
   esta PC puntual → las guarda en
   `C:\ProgramData\EncenderPCAgent\device.json`.
2. El servicio de Windows arranca solo con el sistema y, mientras esté
   vivo, cada 30s hace `PATCH` a Firestore poniendo `status: "online"`.
3. Si la PC se apaga prolijamente, el servicio intenta mandar un último
   `status: "offline"` antes de morir. Si el corte es abrupto (falla de
   luz, crash), no llega a avisar — para eso está la Cloud Function
   `checkOfflineDevices`, que marca offline cualquier PC sin heartbeat
   hace más de 90s.
4. Si Windows apaga el servicio inesperadamente (crash), `sc.exe` está
   configurado para reintentar arrancarlo solo, en vez de dejar la PC
   marcada offline hasta que alguien lo note.

## Para desarrolladores: compilar el .exe que se reparte

Requisitos:

- [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
- Windows 10/11 con PowerShell (para compilar el `win-x64` self-contained;
  también se puede cross-compilar desde otro SO con `dotnet publish -r win-x64`).

### Configurar Firebase antes de compilar

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

### Generar el .exe

```powershell
.\build.ps1
```

Esto corre `dotnet publish` en modo self-contained + single-file (no
hace falta tener .NET instalado en la PC destino) y deja el resultado en
`.\publish\EncenderPCAgent.exe`. Ese es el único archivo (más
`appsettings.json`, que se publica al lado) que hay que repartir.

El propio `.exe` reemplaza lo que antes hacían `install.ps1` +
`EncenderPCAgent.exe pair` + `Start-Service` a mano: pide UAC, registra
el servicio con `sc.exe`, corre el emparejamiento y arranca el servicio,
todo en un solo doble click.

### Reinstalar / actualizar el código

Volvé a compilar con `.\build.ps1` y repartí el `.exe` nuevo; al
correrlo (doble click) reemplaza el servicio existente pero **no toca**
`C:\ProgramData\EncenderPCAgent\device.json`, así que no hace falta
volver a emparejar.

### Modo consola / avanzado

Para quien prefiera no usar el wizard interactivo:

```powershell
EncenderPCAgent.exe pair        # solo emparejar (reinicia el servicio si ya existía)
EncenderPCAgent.exe uninstall   # da de baja el servicio
```

Ver logs del servicio (van al Visor de eventos de Windows):

```powershell
Get-EventLog -LogName Application -Source "EncenderPCAgent" -Newest 20
```

## Estructura

```
EncenderPCAgent.csproj
appsettings.json          Config pública (projectId, apiKey de Firebase)
Program.cs                 Entry point: wizard vs "pair" vs "uninstall" vs servicio
Worker.cs                   Loop de heartbeat + renovación de token
Config/
  AgentConfig.cs              POCOs de configuración
  ConfigStore.cs               Guarda/lee device.json en ProgramData
Firebase/
  FirebaseAuthClient.cs         signInWithCustomToken + refresh
  PairingClient.cs               Llama a la Cloud Function pairDevice
  FirestoreHeartbeatClient.cs     PATCH de status/lastSeen a Firestore
Pairing/
  PairingFlow.cs                  Flujo interactivo de emparejamiento
Installer/
  ServiceInstaller.cs             Instala/arranca/reinicia el servicio (sc.exe) + UAC
  SetupWizard.cs                   Flujo de doble click: UAC → instalar → emparejar → arrancar
build.ps1                  Compila el .exe self-contained a repartir (uso del desarrollador)
uninstall.ps1              Atajo para "EncenderPCAgent.exe uninstall" + borrar credenciales
```
