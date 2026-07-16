# EncenderPCCompanion — Backend (Firestore + Cloud Functions)

Backend serverless en Firebase para la parte de "estado de la PC" y
notificaciones push del proyecto EncenderPCCompanion.

## Qué hace

- Cuentas de usuario → **Firebase Auth**.
- Registro de PCs y su estado (online/offline) → **Firestore**.
- Emparejar la PC con la cuenta de forma segura, sin exponer
  credenciales de admin en el agente de Windows → **Cloud Functions**.
- Detectar que una PC se apagó (o se colgó) sin que avise → **Cloud
  Function programada (cron)**.
- Avisar al celular apenas la PC se prende → **FCM (push)**.

## Flujo completo

1. Usuario crea cuenta en la app (Firebase Auth).
2. Usuario toca "Agregar PC" → la app llama a `createDevice` → se crea
   `devices/{deviceId}` (status: offline) y se muestra un **código de
   6 dígitos** válido por 10 minutos.
3. Usuario abre el agente de Windows, tipea el código → el agente
   llama a `pairDevice` → recibe un **custom token** que solo le da
   permiso de escribir `status`/`lastSeen` de esa PC puntual.
4. El agente manda un heartbeat (`status: "online"`, `lastSeen: now`)
   cada ~30s mientras Windows está prendido.
5. `checkOfflineDevices` (corre cada 1 min) pasa a `offline`
   cualquier PC cuyo `lastSeen` tenga más de 90s — cubre apagados,
   crashes o cortes de red.
6. `onDeviceStatusChange` detecta el flanco `offline → online` y le
   manda una push (FCM) al dueño: *"Tu PC se encendió"*.
7. La app Android muestra el estado en tiempo real con un listener de
   Firestore sobre `devices` (no hace falta polling manual).

## Requisitos

- Node.js 20+
- Cuenta de Firebase (plan **Blaze** — pay-as-you-go. Es obligatorio
  para usar Cloud Functions, pero tiene cuota gratis mensual grande;
  a la escala de este proyecto no debería generar costo real).
- Firebase CLI: `npm install -g firebase-tools`

## Setup

```bash
firebase login
firebase use --add          # elegí o creá tu proyecto de Firebase
cd functions
npm install
cd ..
```

## Emulador local (para probar sin gastar cuota real)

```bash
cd functions
npm run serve
```

Esto levanta el emulador de Functions + Firestore. Podés probar
`createDevice` y `pairDevice` desde el emulator UI o con `curl`/Postman
usando el SDK de Firebase Auth para conseguir un ID token de prueba.

## Deploy a producción

```bash
firebase deploy --only firestore:rules,firestore:indexes,functions
```

## Estructura

```
firebase.json              Config del proyecto (functions + reglas)
firestore.rules            Reglas de seguridad (quién puede leer/escribir qué)
firestore.indexes.json     Índice compuesto para la query de "PCs sin heartbeat"
functions/
  src/
    admin.ts                Init de firebase-admin + constantes (umbral offline)
    pairing.ts               createDevice, pairDevice
    presence.ts               checkOfflineDevices, onDeviceStatusChange
    users.ts                   onUserCreate, registerFcmToken
    index.ts                    Exporta todo
```

## Próximos pasos (fuera de este backend)

- **Agente de Windows** (.NET 8 Worker Service): intercambia el
  `customToken` por credenciales reales via
  `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken`,
  y después hace `PATCH` a la REST API de Firestore
  (`https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents/devices/{deviceId}`)
  cada 30s con el `idToken` como `Authorization: Bearer`.
- **App Android**: agregar Firebase Auth, el SDK de Firestore
  (listener en `devices` filtrado por `ownerUid`), el SDK de FCM, y
  las pantallas de login / "Mis PCs" / "Agregar PC".

Pedime cualquiera de las dos y seguimos con el código.
