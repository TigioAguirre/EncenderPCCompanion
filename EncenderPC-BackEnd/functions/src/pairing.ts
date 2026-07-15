import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";

const PAIRING_CODE_TTL_MINUTES = 10;

function generatePairingCode(): string {
  // Código de 6 dígitos, fácil de tipear a mano en la PC.
  return Math.floor(100000 + Math.random() * 900000).toString();
}

/**
 * Llamada desde la app Android cuando el usuario toca "Agregar PC".
 * Crea el documento de la PC (offline por defecto) y un código de
 * emparejamiento de un solo uso que el usuario tipea en el agente de Windows.
 */
export const createDevice = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Tenés que estar logueado.");
  }
  const ownerUid = request.auth.uid;
  const name: string = (request.data?.name || "Mi PC").toString().slice(0, 60);

  const deviceRef = db.collection("devices").doc();
  await deviceRef.set({
    ownerUid,
    name,
    status: "offline",
    lastSeen: null,
    createdAt: FieldValue.serverTimestamp(),
  });

  const code = generatePairingCode();
  const expiresAt = new Date(Date.now() + PAIRING_CODE_TTL_MINUTES * 60 * 1000);

  await db.collection("pairingCodes").doc(code).set({
    deviceId: deviceRef.id,
    ownerUid,
    used: false,
    expiresAt,
  });

  return {
    deviceId: deviceRef.id,
    pairingCode: code,
    expiresInMinutes: PAIRING_CODE_TTL_MINUTES,
  };
});

/**
 * Llamada desde el agente de Windows (SIN sesión de Firebase todavía) con
 * el código de 6 dígitos que el usuario tipeó. Devuelve un custom token
 * que el agente cambia por credenciales reales (signInWithCustomToken) y
 * que, gracias al claim "deviceId", solo le permite escribir status/lastSeen
 * de ESA PC puntual (ver firestore.rules).
 */
export const pairDevice = onCall(async (request) => {
  const code: string = (request.data?.pairingCode || "").toString().trim();
  if (!/^\d{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Código inválido.");
  }

  const codeRef = db.collection("pairingCodes").doc(code);
  const codeSnap = await codeRef.get();

  if (!codeSnap.exists) {
    throw new HttpsError("not-found", "Ese código no existe o ya expiró.");
  }

  const codeData = codeSnap.data()!;
  if (codeData.used) {
    throw new HttpsError("failed-precondition", "Ese código ya fue usado.");
  }
  if (codeData.expiresAt.toDate() < new Date()) {
    throw new HttpsError("deadline-exceeded", "Ese código expiró, generá uno nuevo desde la app.");
  }

  const deviceId: string = codeData.deviceId;

  await codeRef.update({ used: true });

  // Identidad sintética para el agente: no es un usuario real, es "la PC".
  const customToken = await getAuth().createCustomToken(`device:${deviceId}`, {
    deviceId,
  });

  return { customToken, deviceId };
});
