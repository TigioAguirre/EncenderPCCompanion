# Usar tu propio proyecto de Firebase (self-hosting)

Esta guía es para quien quiere levantar **su propia instancia completa**
del backend, sin depender del proyecto de Firebase del autor ni de los
`.apk`/`.exe` ya compilados en [`Executables/`](../Executables). Al
terminar vas a tener tu propio proyecto Firebase, tu propio APK firmado
con esa config y tu propio agente de Windows apuntando a él.

> Si solo querés **usar** la app tal cual la publica el autor, no
> necesitás nada de esto — mirá el ["Instalación rápida"](../README.md#instalación-rápida-sin-compilar-nada)
> del README raíz.

## Por qué hace falta esto

Los tres proyectos del repo están todos atados al mismo proyecto de
Firebase (`encenderpc-companion`) en tres archivos distintos:

| Archivo | Proyecto | Qué contiene |
|---|---|---|
| `EncenderPC-BackEnd/.firebaserc` | Backend | A qué proyecto de Firebase le hace `firebase deploy` |
| `EncenderPCCompanion/app/google-services.json` | App Android | Config del SDK de Firebase (Auth, Firestore, Functions, FCM) |
| `EncenderPCAgent/appsettings.json` | Agente Windows | `ProjectId` y `ApiKey` para hablar por REST con Firebase Auth y Firestore |

Si compilás la app o el agente sin cambiar estos tres archivos, van a
intentar conectarse al proyecto del autor (y van a fallar, porque las
`firestore.rules` bloquean a cualquiera que no tenga credenciales
válidas de ese proyecto puntual).

Ninguno de estos valores es secreto — son API keys públicas de cliente,
pensadas para viajar dentro de un `.apk` o un `.exe`. Lo que realmente
protege los datos son las `firestore.rules`, que se despliegan igual en
tu propio proyecto.

## Paso 1 — Crear tu proyecto de Firebase

1. Andá a la [Firebase Console](https://console.firebase.google.com/) →
   **Agregar proyecto** → ponele el nombre que quieras.
2. Pasalo al plan **Blaze** (pay-as-you-go). Es obligatorio para poder
   desplegar Cloud Functions, pero la cuota gratis mensual es grande —
   a la escala de este proyecto (un puñado de PCs propias) no debería
   generarte costo real.
3. Dentro del proyecto, activá estos tres productos (todos desde el
   menú lateral):
   - **Authentication** → pestaña "Sign-in method" → habilitá el
     proveedor **Correo electrónico/contraseña** (es el que usa la app
     para las cuentas de usuario).
   - **Firestore Database** → **Crear base de datos** → modo
     producción, la región que prefieras (recomendado: la misma que
     vas a usar para las Functions, ver Paso 2).
   - **Cloud Messaging** → no requiere configuración extra en la
     consola, se activa solo al registrar la app Android (Paso 3).

## Paso 2 — Backend (`EncenderPC-BackEnd/`)

```bash
npm install -g firebase-tools   # si no lo tenés
firebase login
cd EncenderPC-BackEnd
firebase use --add              # elegí tu proyecto nuevo, alias "default"
```

Esto reescribe `.firebaserc` con el `project_id` de tu proyecto — es el
único cambio manual que necesita el backend en sí, el resto de los
archivos (`firebase.json`, `firestore.rules`, `firestore.indexes.json`,
`functions/src/*`) no dependen del proyecto y podés dejarlos como
están.

Instalá dependencias y desplegá:

```bash
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```

Si querés probar en el emulador antes de tocar cuota real:

```bash
cd functions
npm run serve
```

Anotá la **región de las Functions** que te haya quedado (por defecto
`us-central1`, salvo que la hayas cambiado en `firebase.json`) — la vas
a necesitar en el Paso 4.

## Paso 3 — App Android (`EncenderPCCompanion/`)

1. En la Firebase Console, dentro de tu proyecto: ⚙️ **Configuración del
   proyecto** → pestaña **General** → **Agregar app** → ícono de
   Android.
2. Como **nombre de paquete de Android** poné exactamente
   `com.example.bandcolorreact` (tiene que coincidir con el
   `applicationId` de `app/build.gradle`; si le cambiaste el nombre de
   paquete al proyecto, usá el que hayas puesto ahí en su lugar).
3. No hace falta SHA-1 para esta app (no usa Google Sign-In). Al
   terminar el asistente, descargá el archivo `google-services.json`
   que te ofrece.
4. Reemplazá el archivo existente:

```bash
cp ~/Downloads/google-services.json EncenderPCCompanion/app/google-services.json
```

5. Compilá normalmente:

```bash
cd EncenderPCCompanion
./gradlew assembleDebug     # o assembleRelease si vas a firmarlo
```

El APK resultante en `app/build/outputs/apk/` ya va a apuntar a tu
proyecto de Firebase.

> `google-services.json` **sí está versionado** en este repo (a
> propósito, para que cualquiera pueda clonar y compilar sin pasos
> extra usando el proyecto del autor). Si vas a publicar tu propio fork
> públicamente, es buena práctica cambiarlo por el tuyo antes de subirlo
> — como se explica arriba — así no mezclás tus datos con los del
> proyecto original.

## Paso 4 — Agente de Windows (`EncenderPCAgent/`)

1. Abrí `EncenderPCAgent/appsettings.json`.
2. En Firebase Console → ⚙️ Configuración del proyecto → pestaña
   **General** → sección "Tus apps": si no tenés ninguna app **Web**
   todavía, hacé click en el ícono `</>` para registrar una (no hace
   falta hosting, solo el registro) — te va a mostrar un bloque
   `firebaseConfig` con un `apiKey`.
3. Completá el archivo con tu `projectId` y ese `apiKey`:

```json
{
  "Firebase": {
    "ProjectId": "tu-proyecto-id",
    "ApiKey": "AIza....................",
    "FunctionsRegion": "us-central1"
  },
  "Agent": {
    "HeartbeatIntervalSeconds": 30
  }
}
```

`FunctionsRegion` tiene que coincidir con la región donde quedaron
desplegadas tus Cloud Functions (Paso 2) — si no la tocaste, dejá
`us-central1`.

4. Compilá el ejecutable:

```powershell
cd EncenderPCAgent
.\build.ps1
```

Esto deja `EncenderPCAgent.exe` junto a su `appsettings.json` en
`.\publish\` — son los dos archivos que hay que repartir juntos (el
`.exe` solo, sin el `appsettings.json` al lado, no tiene a qué proyecto
conectarse).

## Paso 5 — Probar el flujo completo

1. Instalá tu APK en el celular, creá una cuenta y agregá una PC → te
   va a mostrar un código de 6 dígitos.
2. Corré tu `EncenderPCAgent.exe` en la PC a monitorear y tipeá ese
   código cuando lo pida.
3. A los ~30s la app debería mostrar la PC como "online". Si desconectás
   la red de la PC, en ~90s debería pasar a "offline" y deberías recibir
   la notificación push correspondiente.

Si algo no anda, lo más común es un desajuste entre estos tres archivos
(`ProjectId`/`ApiKey` del agente, `google-services.json` de la app, y el
proyecto activo en `.firebaserc`) — verificá que los tres apunten al
mismo `project_id`, y revisá los logs de las Functions con
`firebase functions:log`.

## Resumen — qué archivo tocar según qué estés compilando

| Vas a compilar | Archivo a editar | Con qué |
|---|---|---|
| Backend (`firebase deploy`) | `EncenderPC-BackEnd/.firebaserc` | `firebase use --add` |
| App Android | `EncenderPCCompanion/app/google-services.json` | Descargado desde Firebase Console (app Android) |
| Agente de Windows | `EncenderPCAgent/appsettings.json` | `apiKey` del bloque `firebaseConfig` (app Web) + tu `ProjectId` |
